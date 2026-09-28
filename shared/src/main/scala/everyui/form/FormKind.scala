package everyui.form

/** What a [[Form]] is for: signing in, making an account, changing a password, getting in touch, paying, or anything
  * else. A password manager offers to save what a sign-in, a sign-up or a password change was given. */
enum FormKind {
  case SignIn, SignUp, ChangePassword, Contact, Payment, Other
}
