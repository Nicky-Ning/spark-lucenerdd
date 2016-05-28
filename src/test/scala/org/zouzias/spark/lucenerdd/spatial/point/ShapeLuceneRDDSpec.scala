/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zouzias.spark.lucenerdd.spatial.point

import java.io.StringWriter

import com.holdenkarau.spark.testing.SharedSparkContext
import com.spatial4j.core.distance.DistanceUtils
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.zouzias.spark.lucenerdd.implicits.LuceneRDDImplicits._
import org.zouzias.spark.lucenerdd.models.SparkDoc
import org.zouzias.spark.lucenerdd.spatial.ContextLoader
import org.zouzias.spark.lucenerdd.spatial.implicits.ShapeLuceneRDDImplicits._

class ShapeLuceneRDDSpec extends FlatSpec
  with Matchers
  with BeforeAndAfterEach
  with SharedSparkContext
  with ContextLoader {

  val Bern = ( (7.45, 46.95), "Bern")
  val Zurich = ( (8.55, 47.366667), "Zurich")
  val Laussanne = ( (6.6335, 46.519833), "Laussanne")
  val Athens = ((23.716667, 37.966667), "Athens")
  val Toronto = ((-79.4, 43.7), "Toronto")
  val k = 5

  var pointLuceneRDD: ShapeLuceneRDD[_, _] = _

  override def afterEach() {
    pointLuceneRDD.close()
  }

  // Check if sequence is sorted in descending order
  def sortedDesc(seq : Seq[Float]) : Boolean = {
    if (seq.isEmpty) true else seq.zip(seq.tail).forall(x => x._1 >= x._2)
  }

  "ShapeLuceneRDD.knn" should "return k-nearest neighbors (knn)" in {

    val cities = Array(Bern, Zurich, Laussanne, Athens, Toronto)
    val rdd = sc.parallelize(cities)
    pointLuceneRDD = ShapeLuceneRDD(rdd)

    val results = pointLuceneRDD.knnSearch(Bern._1, k)

    results.size should equal(k)

    // Closest is Bern and fartherst is Toronto
    docTextFieldEq(results.head.doc, "_1", Bern._2) should equal(true)
    docTextFieldEq(results.last.doc, "_1", Toronto._2) should equal(true)

    // Distances must be sorted
    val revertedDists = results.map(_.score).toList.reverse
    sortedDesc(revertedDists)
  }

  private def docTextFieldEq(doc: SparkDoc, fieldName: String, fieldValue: String): Boolean = {
    doc.textField(fieldName).forall(_.contains(fieldValue))
  }

  "ShapeLuceneRDD.circleSearch" should "return correct results" in {
    val cities = Array(Bern, Zurich, Laussanne, Athens, Toronto)
    val rdd = sc.parallelize(cities)
    pointLuceneRDD = ShapeLuceneRDD(rdd)

    // Bern, Laussanne and Zurich is within 300km
    val results = pointLuceneRDD.circleSearch(Bern._1, 300, k)

    results.size should equal(3)

    results.exists(x => docTextFieldEq(x.doc, "_1", Bern._2)) should equal(true)
    results.exists(x => docTextFieldEq(x.doc, "_1", Zurich._2)) should equal(true)
    results.exists(x => docTextFieldEq(x.doc, "_1", Laussanne._2)) should equal(true)

    results.exists(x => docTextFieldEq(x.doc, "_1", Athens._2)) should equal(false)
    results.exists(x => docTextFieldEq(x.doc, "_1", Toronto._2)) should equal(false)
  }

  "ShapeLuceneRDD.spatialSearch" should "return radius search" in {
    val cities = Array(Bern, Zurich, Laussanne, Athens, Toronto)
    val rdd = sc.parallelize(cities)
    pointLuceneRDD = ShapeLuceneRDD(rdd)

    val point = ctx.makePoint(Bern._1._1, Bern._1._2)
    val circle = ctx.makeCircle(point,
      DistanceUtils.dist2Degrees(300, DistanceUtils.EARTH_MEAN_RADIUS_KM))

    val writer = new StringWriter()
    shapeWriter.write(writer, circle)
    val circleWKT = writer.getBuffer.toString

    // Bern, Laussanne and Zurich is within 300km
    val results = pointLuceneRDD.spatialSearch(circleWKT, k)

    results.size should equal(3)

    results.exists(x => docTextFieldEq(x.doc, "_1", Bern._2)) should equal(true)
    results.exists(x => docTextFieldEq(x.doc, "_1", Zurich._2)) should equal(true)
    results.exists(x => docTextFieldEq(x.doc, "_1", Laussanne._2)) should equal(true)

    results.exists(x => docTextFieldEq(x.doc, "_1", Athens._2)) should equal(false)
    results.exists(x => docTextFieldEq(x.doc, "_1", Toronto._2)) should equal(false)
  }
}
