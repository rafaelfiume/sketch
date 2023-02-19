package org.fiume.sketch

import scala.scalajs.js.typedarray.Int8Array
import scala.scalajs.js.typedarray.ArrayBuffer
import org.scalajs.dom.Event
import org.scalajs.dom.File
import org.scalajs.dom.FileReader
import scala.concurrent.Future
import scala.concurrent.Promise

object Files:

  def readFileAsByteArray(file: File): Future[Array[Byte]] =
    val reader = new FileReader()
    val promise = Promise[Array[Byte]]()

    reader.onload = (_: Event) =>
      val arrayBuffer = reader.result.asInstanceOf[ArrayBuffer]
      val uint8Array = new Int8Array(arrayBuffer)
      promise.success(uint8Array.toArray)

    reader.readAsArrayBuffer(file)

    promise.future
