package everyui.component.types

import reactify.*

/** What a text field asks for, so the platform's autofill and a password manager can fill it: a sign-in, a person's
  * name, an email address, a phone number, an address, a card. `Off` keeps them out of a field that is none of these
  * (a search box, notes, a guest's name). The web says it as the field's `autocomplete`, with a `name` and an `id` to
  * match and the keyboard a phone should show; Android's `autofillHints` and Apple's `textContentType` are where the
  * other platforms say it. */
enum Autofill {
  case Off
  case Username, CurrentPassword, NewPassword
  /** A one-time code of digits (a text message's), typed on a phone's number pad. */
  case OneTimeCode
  /** A one-time code of letters and digits, filled as a one-time code is, typed on the whole keyboard as it is written:
    * nothing suggested or corrected, no capitals put in. */
  case OneTimeCodeText
  case Email, Tel, Url
  case Name, GivenName, AdditionalName, FamilyName, Nickname, Organization
  case StreetAddress, AddressLine1, AddressLine2, Locality, Region, PostalCode, Country
  case CardName, CardNumber, CardExpiry, CardExpiryMonth, CardExpiryYear, CardCsc
  case Birthday
}

object Autofill {
  /** What a field with no hint of its own (`autofill` None) asks for, for the whole app: None, the default, says
    * nothing, and the platform and the password managers guess; `Some(Off)` keeps them out of every field not said to
    * be for something, where an app's only sign-in says what its fields are and the rest (a chat's composer, a
    * search box, a filter) are never to be offered a saved login. A field's own hint always wins. Fields already made
    * follow a change.
    * {{{
    * Autofill.fallback @= Some(Autofill.Off)
    * }}} */
  val fallback: Var[Option[Autofill]] = Var(None)

  /** What a field hinted `hint` asks for in the end: its own hint, else the app's `fallback`. */
  def resolve(hint: Option[Autofill]): Option[Autofill] = hint.orElse(fallback())

  /** Whether it asks for a password, which is hidden as it is typed. */
  def secret(autofill: Autofill): Boolean = autofill == Autofill.CurrentPassword || autofill == Autofill.NewPassword

  /** Whether what it asks for reads left to right in every language: a sign-in, a code, an email address, a phone
    * number, a web address (`InputView.direction`). */
  def leftToRight(autofill: Autofill): Boolean = autofill match {
    case Autofill.Username | Autofill.CurrentPassword | Autofill.NewPassword | Autofill.OneTimeCode |
         Autofill.OneTimeCodeText | Autofill.Email |
         Autofill.Tel | Autofill.Url => true
    case _ => false
  }

  /** Its name in the web's `autocomplete` vocabulary ("name", "email", "tel", "current-password", "off"), which
    * Android's hints and Apple's content types are mapped from: the seam's word for it. */
  def token(autofill: Autofill): String = autofill match {
    case Autofill.Off => "off"
    case Autofill.Username => "username"
    case Autofill.CurrentPassword => "current-password"
    case Autofill.NewPassword => "new-password"
    case Autofill.OneTimeCode => "one-time-code"
    // the platform's one-time code, on the whole keyboard (each backend says it as "one-time-code")
    case Autofill.OneTimeCodeText => "one-time-code-text"
    case Autofill.Email => "email"
    case Autofill.Tel => "tel"
    case Autofill.Url => "url"
    case Autofill.Name => "name"
    case Autofill.GivenName => "given-name"
    case Autofill.AdditionalName => "additional-name"
    case Autofill.FamilyName => "family-name"
    case Autofill.Nickname => "nickname"
    case Autofill.Organization => "organization"
    case Autofill.StreetAddress => "street-address"
    case Autofill.AddressLine1 => "address-line1"
    case Autofill.AddressLine2 => "address-line2"
    case Autofill.Locality => "address-level2"
    case Autofill.Region => "address-level1"
    case Autofill.PostalCode => "postal-code"
    case Autofill.Country => "country"
    case Autofill.CardName => "cc-name"
    case Autofill.CardNumber => "cc-number"
    case Autofill.CardExpiry => "cc-exp"
    case Autofill.CardExpiryMonth => "cc-exp-month"
    case Autofill.CardExpiryYear => "cc-exp-year"
    case Autofill.CardCsc => "cc-csc"
    case Autofill.Birthday => "bday"
  }
}
