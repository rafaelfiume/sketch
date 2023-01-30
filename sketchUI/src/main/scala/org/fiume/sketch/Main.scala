package org.fiume.sketch

import com.raquo.laminar.api.L.{*, given} // HTML tags and CSS properties are within the scope
import org.scalajs.dom

// Laminar bootstrap

@main
def DocumentsForm(): Unit =
  renderOnDomContentLoaded( // entry point for laminar
    dom.document.querySelector("#app"),
    FormSkeleton.RegisterForm()
  )
