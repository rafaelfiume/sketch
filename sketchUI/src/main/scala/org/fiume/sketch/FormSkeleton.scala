package org.fiume.sketch

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.fiume.sketch.domain.Document
import org.scalajs.dom.html.Form
import org.scalajs.dom
import scala.concurrent.Future

/*
 * Heavily inspired on https://blog.softwaremill.com/hands-on-laminar-354ddcc536a9
 */
object FormSkeleton:

  case class FormState(docName: Option[String], description: Option[String], file: Option[String])

  def make(storage: Storage[Future, Document.Metadata]): ReactiveHtmlElement[Form] =
    val $formSend = Var(false)
    val $formState = Var(FormState(None, None, None))
    val $nameValue = Var("")
    val $descriptionValue = Var("")
    form(
      onSubmit.preventDefault.map(_ => true) --> $formSend,
      Header(),
      Name($formSend.signal, $formState.updater((state, newName) => state.copy(docName = newName))),
      Description($formSend.signal, $formState.updater((state, newDescription) => state.copy(description = newDescription))),
      File($formSend.signal, $formState.updater((state, newFile) => state.copy(file = newFile))),
      Store($formState, storage)
    )

  private def Name($formSend: Signal[Boolean], $observer: Observer[Option[String]]): HtmlElement =
    def nonEmptyName(s: String) = if s.isEmpty then Some("Name cannot be empty") else None
    val $name = Var("")
    val $nameTouched = Var[Boolean](false)
    Input.Text(
      "Name:",
      "name",
      Observer.combine(
        $observer.contramap { name => if nonEmptyName(name).isEmpty then Some(name) else None },
        $name.writer
      ),
      InputStateConfig(touched = $nameTouched.writer),
      $name.signal
        .map(nonEmptyName)
        .combineWith($formSend, $nameTouched.signal)
        .map({ case (nameValidationResult, formSend, nameTouched) =>
          if formSend || nameTouched then nameValidationResult else None
        })
    )

  private def Description($formSend: Signal[Boolean], $observer: Observer[Option[String]]): HtmlElement =
    val $description = Var("")
    val $descriptionTouched = Var[Boolean](false)
    Input.Text(
      "Description:",
      "descr",
      $observer.contramap { Some(_) },
      InputStateConfig(touched = $descriptionTouched.writer)
    )

  private def File($formSend: Signal[Boolean], $observer: Observer[Option[String]]): HtmlElement =
    def nonEmptyFile(s: String) = if s.isEmpty then Some("Select a file to be uploaded") else None
    val $file = Var("")
    val $fileTouched = Var[Boolean](false)
    Input.File(
      "File:",
      "file",
      "image/*,.pdf,.doc,.xml",
      Observer.combine(
        $observer.contramap { Some(_) },
        $file.writer
      ),
      InputStateConfig(touched = $fileTouched.writer),
      $file.signal
        .map(nonEmptyFile)
        .combineWith($formSend, $fileTouched.signal)
        .map({ case (fileValidationResult, formSend, fileTouched) =>
          if formSend || fileTouched then fileValidationResult else None
        })
    )

  private def Header(): HtmlElement = div("Documents")

  private def Store($formState: Var[FormState], storage: Storage[Future, Document.Metadata]): HtmlElement =
    // TODO......
    //import cats.effect.unsafe.implicits.global
    val $payload = Var[Option[Document.Metadata]](None)
    div(
      button(
        "Store",
        inContext { thisNode =>
          val $click = thisNode.events(onClick).sample($formState.signal)
          val $response = $click.flatMap { state =>
            val metadata = Document.Metadata(
              Document.Metadata.Name(state.docName.get),
              Document.Metadata.Description(state.description.getOrElse(""))
            )

            EventStream.fromFuture(
              storage.storeDocument(metadata, state.file.get),
              true
            )
          }
          List(
            $response.map(Some(_)) --> $payload.writer
          )
        }
      ),
      // div(children <-- $formState.signal.map { s => List(div(s.docName), div(s.description), div(s.file)) }),
      div(children <-- $payload.signal.map { p => List(div(p.toString)) })
    )

  object Input:
    // returns Some("string") when validation fails or None when it succeeds
    type Validator = Signal[Option[String]]

    def Text(
      labelText: String,
      id: String,
      valueObserver: Observer[String],
      inputStateConfig: InputStateConfig,
      validators: Validator*
    ): HtmlElement =
      val $value = Var("")
      val $collectedErrors = collectErrors(validators)
      val $invalid = $collectedErrors.map(_.nonEmpty)
      div(
        // InputStyles.inputWrapper, // possible to setup styles in divs....
        cls.toggle("invalid") <-- $invalid,
        div(
          label(
            labelText,
            forId := id
          ),
          input(
            inputStateMod(inputStateConfig, $invalid),
            `type` := "text",
            idAttr := id,
            name := id,
            onInput.mapToValue --> valueObserver,
            onInput.mapToValue --> $value,
            cls.toggle("non-empty") <-- $value.signal.map(_.nonEmpty)
          ),
          Errors($collectedErrors)
        )
      )

    def File(
      labelText: String,
      id: String,
      typeOfFilesAccepted: String,
      valueObserver: Observer[String],
      inputStateConfig: InputStateConfig,
      validators: Validator*
    ): HtmlElement =
      val $value = Var("")
      val $collectedErrors = collectErrors(validators)
      val $invalid = $collectedErrors.map(_.nonEmpty)
      div(
        // InputStyles.inputWrapper, // possible to setup styles in divs....
        cls.toggle("invalid") <-- $invalid,
        div(
          label(
            labelText,
            forId := id
          ),
          input(
            inputStateMod(inputStateConfig, $invalid),
            `type` := "file",
            idAttr := id,
            name := id,
            accept := typeOfFilesAccepted,
            onChange.mapToValue --> valueObserver,
            onInput.mapToValue --> $value,
            cls.toggle("non-empty") <-- $value.signal.map(_.nonEmpty)
          ),
          Errors($collectedErrors)
        )
      )

    private def Errors($errors: Signal[Seq[String]]) =
      div(
        children <-- $errors.map(_.map(div(_)))
      )

    private def collectErrors(validators: Seq[Validator]): Signal[Seq[String]] =
      Signal.combineSeq(validators).map { validatorsSeq =>
        validatorsSeq.collect { case Some(errorMessage) => errorMessage }
      }

    private def inputStateMod(inputStateConfig: InputStateConfig, $invalid: Signal[Boolean]): Mod[Input] =
      val $dirty = Var(false)
      val $touched = Var(false)
      val $untouched: Signal[Boolean] = $touched.signal.map(touched => !touched)
      val $pristine: Signal[Boolean] = $dirty.signal.map(dirty => !dirty)
      val $valid = $invalid.map(invalid => !invalid)
      List(
        $touched --> inputStateConfig.touched,
        $untouched --> inputStateConfig.untouched,
        $dirty --> inputStateConfig.dirty,
        $pristine --> inputStateConfig.pristine,
        $valid --> inputStateConfig.valid,
        $invalid --> inputStateConfig.invalid,
        onBlur.map(_ => true) --> $touched,
        onInput.map(_ => true) --> $dirty
      )

case class InputStateConfig(touched: Observer[Boolean] = Observer.empty,
                            untouched: Observer[Boolean] = Observer.empty,
                            dirty: Observer[Boolean] = Observer.empty,
                            pristine: Observer[Boolean] = Observer.empty,
                            valid: Observer[Boolean] = Observer.empty,
                            invalid: Observer[Boolean] = Observer.empty
)

object InputStateConfig:
  def empty(): InputStateConfig = InputStateConfig()

object Helper:
  extension [A]($observer: Observer[Option[A]])
    def emitIfValid(validator: A => Option[A]): Observer[A] =
      $observer.contramap(value => if validator(value).isEmpty then Some(value) else None)

  extension [A]($signal: Signal[Option[A]])
    def validateIf($activationSignal: Signal[Boolean]): Signal[Option[A]] =
      $activationSignal.combineWith($signal).map({
        case (activationSignal, signal) if activationSignal => signal
        case _                                              => None
      })

  extension ($signal: Signal[Boolean])
    def and($secondSignal: Signal[Boolean]): Signal[Boolean] =
      $signal.combineWith($secondSignal)
        .map({ case (signal, secondSignal) => signal && secondSignal })

    def or($secondSignal: Signal[Boolean]): Signal[Boolean] =
      $signal.combineWith($secondSignal)
        .map({ case (signal, secondSignal) => signal || secondSignal })
