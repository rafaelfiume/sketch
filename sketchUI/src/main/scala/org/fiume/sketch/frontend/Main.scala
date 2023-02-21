package org.fiume.sketch.frontend

import com.raquo.laminar.api.L.{*, given} // HTML tags and CSS properties are within the scope
import org.scalajs.dom
import org.fiume.sketch.frontend.storage.StorageHttpClient
import org.fiume.sketch.frontend.storage.ui.FormSkeleton

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
