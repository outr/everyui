package everyui.component.types

/** What a text field asks for, so the platform's autofill and a password manager can fill it: a sign-in, a person's
  * name, an email address, a phone number, an address, a card. `Off` keeps them out of a field that is none of these
  * (a search box, notes, a guest's name). The web says it as the field's `autocomplete`, with a `name` and an `id` to
  * match and the keyboard a phone should show; Android's `autofillHints` and Apple's `textContentType` are where the
  * other platforms say it. */
enum Autofill {
  case Off
  case Username, CurrentPassword, NewPassword, OneTimeCode
  case Email, Tel, Url
  case Name, GivenName, AdditionalName, FamilyName, Nickname, Organization
  case StreetAddress, AddressLine1, AddressLine2, Locality, Region, PostalCode, Country
  case CardName, CardNumber, CardExpiry, CardExpiryMonth, CardExpiryYear, CardCsc
  case Birthday
}

object Autofill {
  /** Whether it asks for a password, which is hidden as it is typed. */
  def secret(autofill: Autofill): Boolean = autofill == Autofill.CurrentPassword || autofill == Autofill.NewPassword

  /** Its name in the web's `autocomplete` vocabulary ("name", "email", "tel", "current-password", "off"), which
    * Android's hints and Apple's content types are mapped from: the seam's word for it. */
  def token(autofill: Autofill): String = autofill match {
    case Autofill.Off => "off"
    case Autofill.Username => "username"
    case Autofill.CurrentPassword => "current-password"
    case Autofill.NewPassword => "new-password"
    case Autofill.OneTimeCode => "one-time-code"
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
