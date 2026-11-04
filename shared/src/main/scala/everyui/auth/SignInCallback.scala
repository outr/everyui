package everyui.auth

/** Where a sign-in with another site (`SignIn.signInWith`) comes back to, on each platform: what the app registers
  * with the provider as its redirect, the address made from it handed to the app as each sign-in starts.
  *
  *  - `web`: a path on the app's own origin (`/signin/done`), whose page hands the address it was sent to back to the
  *    window that asked (`SignIn.finish`); the redirect is the origin with it.
  *  - `scheme` and `path`: on a phone, the app's own scheme (`com.example.app`, a reverse domain, as Google asks) and a
  *    path after it, the redirect `com.example.app:/signin`, which the app's manifest takes (CONSUMING.md, Signing in
  *    with another site).
  *  - `loopback`: on the desktop, the path the app listens on at 127.0.0.1, at a port chosen for each sign-in (RFC
  *    8252), the redirect `http://127.0.0.1:<port>/signin`. */
case class SignInCallback(web: String = "/signin/done", scheme: String = "", path: String = "/signin",
                          loopback: String = "/signin")
