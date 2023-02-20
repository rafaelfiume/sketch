package org.fiume.sketch

import com.raquo.laminar.api.L.{*, given} // HTML tags and CSS properties are within the scope
import org.scalajs.dom

val host = "http://localhost"
val port = "8080" // backend port
val storage = StorageHttpClient.make(s"$host:$port")

@main
def DocumentsForm(): Unit =
  // dom.window.alert("bli.jpg")

  renderOnDomContentLoaded( // entry point for laminar
    dom.document.querySelector("#app"),
    FormSkeleton.make(storage)
  )
