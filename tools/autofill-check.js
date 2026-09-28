// The web's autofill check (tools/check.sh, run by webcheck's WebDrive on ?demo=autofill): what each form's fields
// say they ask for, then a password manager's fill (the value set, then input and change) and its click on the
// sign-in form's submit button, and a value set with no event at all, read as the field is left (the blur is sent
// here, since a window without the system's focus sends none). The page logs what reached the app
// ("[ui] autofill ..."); this returns the fields as "form:name=autocomplete,..." and what is left of the sign-in form
// once it succeeded. WebDrive runs it as the body of a function.
const forms = [...document.querySelectorAll('form[data-everyui-form]')].map(f => f.dataset.everyuiForm + ':' +
  [...f.querySelectorAll('input:not([data-everyui-username])')].map(i => i.name + '=' + i.getAttribute('autocomplete')).join(','));
const fill = (selector, value) => {
  const field = document.querySelector(selector);
  field.value = value;
  field.dispatchEvent(new Event('input', { bubbles: true }));
  field.dispatchEvent(new Event('change', { bubbles: true }));
};
fill('#email', 'ann@example.com');
fill('input[type=password]', 'hunter22');
document.querySelector('form[data-everyui-form=signin] button[type=submit]').click();
const tel = document.getElementById('tel');
tel.value = '555-0100';
tel.dispatchEvent(new FocusEvent('blur'));
const left = document.querySelectorAll('form[data-everyui-form=signin] input').length;
return forms.join(' ') + ' | signin fields left: ' + left;
