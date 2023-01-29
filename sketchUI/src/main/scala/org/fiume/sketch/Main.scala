package org.fiume.sketch

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

// Laminar bootstrap

@main
def LiveChart(): Unit =
  renderOnDomContentLoaded(  // entry point for laminar
    dom.document.querySelector("#app"), Main.appElement()
  )


object Main:
  def appElement(): Element =
    div(
      h1("Hello Sketch!"),
      a(href := "https://vitejs.dev/guide/features.html", target := "_blank", "Documentation")
    )
