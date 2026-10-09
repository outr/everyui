package everyui.i18n

import reactify.*

/** EveryUI's own words, for what its widgets say when the app gives them nothing to say (OK, Cancel, Save): in the
  * current language (`Strings.language`), so text bound to one follows a language change. They come in the eleven
  * languages below; `add` gives more, or the app's own wording in place of these, as messages by key (an ARB's, read
  * with `Arb.parse`). A language they lack falls back to its base language (`pt` for `pt_BR`), then English. */
object Words {
  private val tables: Var[Map[String, Map[String, String]]] = Var(Map(
    "en" -> Map("ok" -> "OK", "cancel" -> "Cancel", "close" -> "Close", "done" -> "Done", "yes" -> "Yes", "no" -> "No",
      "save" -> "Save", "discard" -> "Don't save", "delete" -> "Delete", "retry" -> "Try again", "search" -> "Search",
      "noResults" -> "No results", "loading" -> "Loading…", "back" -> "Back", "apply" -> "Apply",
      "clear" -> "Clear",
      "larger" -> "Larger", "smaller" -> "Smaller", "defaultSize" -> "Default size",
      "leave" -> "Leave", "stay" -> "Stay", "leaveTitle" -> "Leave the app?",
      "leaveMessage" -> "Changes you made may not be saved."),
    "de" -> Map("ok" -> "OK", "cancel" -> "Abbrechen", "close" -> "Schließen", "done" -> "Fertig", "yes" -> "Ja",
      "no" -> "Nein", "save" -> "Speichern", "discard" -> "Nicht speichern", "delete" -> "Löschen",
      "retry" -> "Erneut versuchen", "search" -> "Suchen", "noResults" -> "Keine Ergebnisse", "loading" -> "Wird geladen…",
      "back" -> "Zurück", "apply" -> "Übernehmen", "clear" -> "Leeren",
      "larger" -> "Größer", "smaller" -> "Kleiner", "defaultSize" -> "Standardgröße",
      "leave" -> "Verlassen", "stay" -> "Bleiben", "leaveTitle" -> "App verlassen?",
      "leaveMessage" -> "Ihre Änderungen werden möglicherweise nicht gespeichert."),
    "es" -> Map("ok" -> "Aceptar", "cancel" -> "Cancelar", "close" -> "Cerrar", "done" -> "Listo", "yes" -> "Sí",
      "no" -> "No", "save" -> "Guardar", "discard" -> "No guardar", "delete" -> "Eliminar", "retry" -> "Reintentar",
      "search" -> "Buscar", "noResults" -> "Sin resultados", "loading" -> "Cargando…", "back" -> "Atrás",
      "apply" -> "Aplicar", "clear" -> "Borrar",
      "larger" -> "Más grande", "smaller" -> "Más pequeño", "defaultSize" -> "Tamaño predeterminado",
      "leave" -> "Salir", "stay" -> "Quedarse", "leaveTitle" -> "¿Salir de la aplicación?",
      "leaveMessage" -> "Es posible que no se guarden los cambios que hayas hecho."),
    "fr" -> Map("ok" -> "OK", "cancel" -> "Annuler", "close" -> "Fermer", "done" -> "Terminé", "yes" -> "Oui",
      "no" -> "Non", "save" -> "Enregistrer", "discard" -> "Ne pas enregistrer", "delete" -> "Supprimer",
      "retry" -> "Réessayer", "search" -> "Rechercher", "noResults" -> "Aucun résultat", "loading" -> "Chargement…",
      "back" -> "Retour", "apply" -> "Appliquer", "clear" -> "Effacer",
      "larger" -> "Plus grand", "smaller" -> "Plus petit", "defaultSize" -> "Taille par défaut",
      "leave" -> "Quitter", "stay" -> "Rester", "leaveTitle" -> "Quitter l'application ?",
      "leaveMessage" -> "Les modifications que vous avez apportées ne seront peut-être pas enregistrées."),
    "it" -> Map("ok" -> "OK", "cancel" -> "Annulla", "close" -> "Chiudi", "done" -> "Fine", "yes" -> "Sì", "no" -> "No",
      "save" -> "Salva", "discard" -> "Non salvare", "delete" -> "Elimina", "retry" -> "Riprova", "search" -> "Cerca",
      "noResults" -> "Nessun risultato", "loading" -> "Caricamento…", "back" -> "Indietro", "apply" -> "Applica",
      "clear" -> "Cancella",
      "larger" -> "Più grande", "smaller" -> "Più piccolo", "defaultSize" -> "Dimensione predefinita",
      "leave" -> "Esci", "stay" -> "Resta", "leaveTitle" -> "Uscire dall'app?",
      "leaveMessage" -> "Le modifiche apportate potrebbero non essere salvate."),
    "nl" -> Map("ok" -> "OK", "cancel" -> "Annuleren", "close" -> "Sluiten", "done" -> "Klaar", "yes" -> "Ja",
      "no" -> "Nee", "save" -> "Opslaan", "discard" -> "Niet opslaan", "delete" -> "Verwijderen",
      "retry" -> "Opnieuw proberen", "search" -> "Zoeken", "noResults" -> "Geen resultaten", "loading" -> "Laden…",
      "back" -> "Terug", "apply" -> "Toepassen", "clear" -> "Wissen",
      "larger" -> "Groter", "smaller" -> "Kleiner", "defaultSize" -> "Standaardgrootte",
      "leave" -> "Verlaten", "stay" -> "Blijven", "leaveTitle" -> "App verlaten?",
      "leaveMessage" -> "Wijzigingen die je hebt aangebracht, worden mogelijk niet opgeslagen."),
    "pt" -> Map("ok" -> "OK", "cancel" -> "Cancelar", "close" -> "Fechar", "done" -> "Concluído", "yes" -> "Sim",
      "no" -> "Não", "save" -> "Salvar", "discard" -> "Não salvar", "delete" -> "Excluir", "retry" -> "Tentar novamente",
      "search" -> "Pesquisar", "noResults" -> "Nenhum resultado", "loading" -> "Carregando…", "back" -> "Voltar",
      "apply" -> "Aplicar", "clear" -> "Limpar",
      "larger" -> "Maior", "smaller" -> "Menor", "defaultSize" -> "Tamanho padrão",
      "leave" -> "Sair", "stay" -> "Ficar", "leaveTitle" -> "Sair do app?",
      "leaveMessage" -> "As alterações feitas talvez não sejam salvas."),
    "tr" -> Map("ok" -> "Tamam", "cancel" -> "İptal", "close" -> "Kapat", "done" -> "Bitti", "yes" -> "Evet",
      "no" -> "Hayır", "save" -> "Kaydet", "discard" -> "Kaydetme", "delete" -> "Sil", "retry" -> "Yeniden dene",
      "search" -> "Ara", "noResults" -> "Sonuç yok", "loading" -> "Yükleniyor…", "back" -> "Geri", "apply" -> "Uygula",
      "clear" -> "Temizle",
      "larger" -> "Daha büyük", "smaller" -> "Daha küçük", "defaultSize" -> "Varsayılan boyut",
      "leave" -> "Ayrıl", "stay" -> "Kal", "leaveTitle" -> "Uygulamadan ayrılınsın mı?",
      "leaveMessage" -> "Yaptığınız değişiklikler kaydedilmeyebilir."),
    "ar" -> Map("ok" -> "موافق", "cancel" -> "إلغاء", "close" -> "إغلاق", "done" -> "تم", "yes" -> "نعم", "no" -> "لا",
      "save" -> "حفظ", "discard" -> "عدم الحفظ", "delete" -> "حذف", "retry" -> "إعادة المحاولة", "search" -> "بحث",
      "noResults" -> "لا توجد نتائج", "loading" -> "جارٍ التحميل…", "back" -> "رجوع", "apply" -> "تطبيق", "clear" -> "مسح",
      "larger" -> "أكبر", "smaller" -> "أصغر", "defaultSize" -> "الحجم الافتراضي",
      "leave" -> "مغادرة", "stay" -> "البقاء", "leaveTitle" -> "هل تريد مغادرة التطبيق؟",
      "leaveMessage" -> "قد لا يتم حفظ التغييرات التي أجريتها."),
    "zh" -> Map("ok" -> "确定", "cancel" -> "取消", "close" -> "关闭", "done" -> "完成", "yes" -> "是", "no" -> "否",
      "save" -> "保存", "discard" -> "不保存", "delete" -> "删除", "retry" -> "重试", "search" -> "搜索",
      "noResults" -> "无结果", "loading" -> "正在加载…", "back" -> "返回", "apply" -> "应用", "clear" -> "清除",
      "larger" -> "放大", "smaller" -> "缩小", "defaultSize" -> "默认大小",
      "leave" -> "离开", "stay" -> "留下", "leaveTitle" -> "要离开应用吗？",
      "leaveMessage" -> "系统可能不会保存您所做的更改。"),
    "zh_Hant" -> Map("ok" -> "確定", "cancel" -> "取消", "close" -> "關閉", "done" -> "完成", "yes" -> "是", "no" -> "否",
      "save" -> "儲存", "discard" -> "不儲存", "delete" -> "刪除", "retry" -> "重試", "search" -> "搜尋",
      "noResults" -> "沒有結果", "loading" -> "正在載入…", "back" -> "返回", "apply" -> "套用", "clear" -> "清除",
      "larger" -> "放大", "smaller" -> "縮小", "defaultSize" -> "預設大小",
      "leave" -> "離開", "stay" -> "留下", "leaveTitle" -> "要離開應用程式嗎？",
      "leaveMessage" -> "系統可能不會儲存你所做的變更。")))

  /** `messages` for language `code`, over those it had: another language, or an app's own wording. */
  def add(code: String, messages: Map[String, String]): Unit =
    tables @= tables() + (code -> (tables().getOrElse(code, Map.empty) ++ messages))

  /** The word for `key` in the current language, its base language, or English; the key itself where none has it. */
  def apply(key: String): String = {
    val code = Strings.language().code
    val base = if (code.contains("_")) code.substring(0, code.indexOf("_")) else code
    val all = tables()
    all.get(code).flatMap(_.get(key))
      .orElse(all.get(base).flatMap(_.get(key)))
      .orElse(all.get("en").flatMap(_.get(key)))
      .getOrElse(key)
  }

  def ok: String = apply("ok")
  def cancel: String = apply("cancel")
  def close: String = apply("close")
  def done: String = apply("done")
  def yes: String = apply("yes")
  def no: String = apply("no")
  def save: String = apply("save")
  /** Leaving unsaved work unsaved: the middle of Save / Don't save / Cancel. */
  def discard: String = apply("discard")
  def delete: String = apply("delete")
  def retry: String = apply("retry")
  def search: String = apply("search")
  /** What empties a field (a search box's ×). */
  def clear: String = apply("clear")
  def noResults: String = apply("noResults")
  def loading: String = apply("loading")
  def back: String = apply("back")
  /** The app drawn a step larger, a step smaller, and at the size it is designed at (UiScale). */
  def larger: String = apply("larger")
  def smaller: String = apply("smaller")
  def defaultSize: String = apply("defaultSize")
  /** The question before the app is left while it is guarded (`Platform.leaveGuard`): its title, what it says, and its
    * two answers. */
  def leaveTitle: String = apply("leaveTitle")
  def leaveMessage: String = apply("leaveMessage")
  def leave: String = apply("leave")
  def stay: String = apply("stay")
}
