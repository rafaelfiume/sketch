package org.fiume.sketch.frontend

import com.raquo.laminar.api.L.{*, given}
import org.fiume.sketch.frontend.storage.StorageHttpClient
import org.fiume.sketch.frontend.storage.ui.FormSkeleton
import org.scalajs.dom

@main
def DocumentsForm(): Unit =
  val host = "http://localhost"
  val port = "8080" // backend port
  val storage = StorageHttpClient.make(s"$host:$port")
  // dom.window.alert("bli.jpg")

  renderOnDomContentLoaded( // entry point for laminar
    dom.document.querySelector("#app"),
    FormSkeleton.make(storage)
  )