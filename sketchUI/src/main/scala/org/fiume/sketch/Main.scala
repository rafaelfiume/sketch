package org.fiume.sketch

import scala.scalajs.js
import org.scalajs.dom

@main
def LiveChart(): Unit = {
    dom.document.querySelector("#app").innerHTML = """
      <h1>Hello Sketch!</h1>
      <a href="https://vitejs.dev/guide/features.html" target="_blank">Documentation</a>
    """
}
