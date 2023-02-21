package org.fiume.sketch.frontend

import org.scalajs.dom.{Event, File, FileReader}
import scala.scalajs.js.typedarray.{ArrayBuffer, Int8Array}
import scala.concurrent.{Future, Promise}

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
