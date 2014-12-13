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

package org.apache.spark.api.python

import java.nio.ByteOrder

import scala.collection.JavaConversions._
import scala.util.Failure
import scala.util.Try

import net.razorvine.pickle.{Unpickler, Pickler}

import org.apache.spark.{Logging, SparkException}
import org.apache.spark.rdd.RDD

/** Utilities for serialization / deserialization between Python and Java, using Pickle. */
private[python] object SerDeUtil extends Logging {
  // Unpickle array.array generated by Python 2.6
  class ArrayConstructor extends net.razorvine.pickle.objects.ArrayConstructor {
    //  /* Description of types */
    //  static struct arraydescr descriptors[] = {
    //    {'c', sizeof(char), c_getitem, c_setitem},
    //    {'b', sizeof(char), b_getitem, b_setitem},
    //    {'B', sizeof(char), BB_getitem, BB_setitem},
    //    #ifdef Py_USING_UNICODE
    //      {'u', sizeof(Py_UNICODE), u_getitem, u_setitem},
    //    #endif
    //    {'h', sizeof(short), h_getitem, h_setitem},
    //    {'H', sizeof(short), HH_getitem, HH_setitem},
    //    {'i', sizeof(int), i_getitem, i_setitem},
    //    {'I', sizeof(int), II_getitem, II_setitem},
    //    {'l', sizeof(long), l_getitem, l_setitem},
    //    {'L', sizeof(long), LL_getitem, LL_setitem},
    //    {'f', sizeof(float), f_getitem, f_setitem},
    //    {'d', sizeof(double), d_getitem, d_setitem},
    //    {'\0', 0, 0, 0} /* Sentinel */
    //  };
    // TODO: support Py_UNICODE with 2 bytes
    // FIXME: unpickle array of float is wrong in Pyrolite, so we reverse the
    // machine code for float/double here to workaround it.
    // we should fix this after Pyrolite fix them
    val machineCodes: Map[Char, Int] = if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
      Map('c' -> 1, 'B' -> 0, 'b' -> 1, 'H' -> 3, 'h' -> 5, 'I' -> 7, 'i' -> 9,
        'L' -> 11, 'l' -> 13, 'f' -> 14, 'd' -> 16, 'u' -> 21
      )
    } else {
      Map('c' -> 1, 'B' -> 0, 'b' -> 1, 'H' -> 2, 'h' -> 4, 'I' -> 6, 'i' -> 8,
        'L' -> 10, 'l' -> 12, 'f' -> 15, 'd' -> 17, 'u' -> 20
      )
    }
    override def construct(args: Array[Object]): Object = {
      if (args.length == 1) {
        construct(args ++ Array(""))
      } else if (args.length == 2 && args(1).isInstanceOf[String]) {
        val typecode = args(0).asInstanceOf[String].charAt(0)
        val data: String = args(1).asInstanceOf[String]
        construct(typecode, machineCodes(typecode), data.getBytes("ISO-8859-1"))
      } else {
        super.construct(args)
      }
    }
  }

  def initialize() = {
    Unpickler.registerConstructor("array", "array", new ArrayConstructor())
  }
}

