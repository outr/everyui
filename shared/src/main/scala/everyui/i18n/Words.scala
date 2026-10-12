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
      "leaveMessage" -> "Changes you made may not be saved.",
      "chat.queued" -> "{n, plural, =0{No messages queued} =1{1 message queued} other{# messages queued}}",
      "chat.queuePaused" -> "Paused", "chat.resume" -> "Resume", "chat.addToQueue" -> "Add to queue",
      "chat.edit" -> "Edit", "chat.remove" -> "Remove", "chat.files" -> "{n, plural, =1{1 file} other{# files}}",
      "chat.queuedItem" -> "Queued message {i} of {n}: {text}",
      "chat.queuedHelp" -> "Enter edits it, Delete removes it, Alt+Up or Alt+Down moves it",
      "chat.moved" -> "Moved to {i} of {n}", "chat.reorder" -> "Drag to reorder",
      "chat.sendNext" -> "Send next", "chat.clearQueue" -> "Clear queue", "chat.queueMore" -> "More queue actions",
      "tabs.maximize" -> "Hide Other Groups", "tabs.unmaximize" -> "Show All Groups"),
    "de" -> Map("ok" -> "OK", "cancel" -> "Abbrechen", "close" -> "Schließen", "done" -> "Fertig", "yes" -> "Ja",
      "no" -> "Nein", "save" -> "Speichern", "discard" -> "Nicht speichern", "delete" -> "Löschen",
      "retry" -> "Erneut versuchen", "search" -> "Suchen", "noResults" -> "Keine Ergebnisse", "loading" -> "Wird geladen…",
      "back" -> "Zurück", "apply" -> "Übernehmen", "clear" -> "Leeren",
      "larger" -> "Größer", "smaller" -> "Kleiner", "defaultSize" -> "Standardgröße",
      "leave" -> "Verlassen", "stay" -> "Bleiben", "leaveTitle" -> "App verlassen?",
      "leaveMessage" -> "Ihre Änderungen werden möglicherweise nicht gespeichert.",
      "chat.queued" -> ("{n, plural, =0{Keine Nachrichten in der Warteschlange} =1{1 Nachricht in der Warteschlange} " +
        "other{# Nachrichten in der Warteschlange}}"),
      "chat.queuePaused" -> "Pausiert", "chat.resume" -> "Fortsetzen", "chat.addToQueue" -> "Zur Warteschlange hinzufügen",
      "chat.edit" -> "Bearbeiten", "chat.remove" -> "Entfernen",
      "chat.files" -> "{n, plural, =1{1 Datei} other{# Dateien}}",
      "chat.queuedItem" -> "Nachricht {i} von {n} in der Warteschlange: {text}",
      "chat.queuedHelp" -> "Eingabe bearbeitet sie, Entf entfernt sie, Alt+Pfeil nach oben oder unten verschiebt sie",
      "chat.moved" -> "Auf Position {i} von {n} verschoben", "chat.reorder" -> "Zum Neuordnen ziehen",
      "chat.sendNext" -> "Nächste senden", "chat.clearQueue" -> "Warteschlange leeren",
      "chat.queueMore" -> "Weitere Aktionen der Warteschlange",
      "tabs.maximize" -> "Andere Gruppen ausblenden", "tabs.unmaximize" -> "Alle Gruppen anzeigen"),
    "es" -> Map("ok" -> "Aceptar", "cancel" -> "Cancelar", "close" -> "Cerrar", "done" -> "Listo", "yes" -> "Sí",
      "no" -> "No", "save" -> "Guardar", "discard" -> "No guardar", "delete" -> "Eliminar", "retry" -> "Reintentar",
      "search" -> "Buscar", "noResults" -> "Sin resultados", "loading" -> "Cargando…", "back" -> "Atrás",
      "apply" -> "Aplicar", "clear" -> "Borrar",
      "larger" -> "Más grande", "smaller" -> "Más pequeño", "defaultSize" -> "Tamaño predeterminado",
      "leave" -> "Salir", "stay" -> "Quedarse", "leaveTitle" -> "¿Salir de la aplicación?",
      "leaveMessage" -> "Es posible que no se guarden los cambios que hayas hecho.",
      "chat.queued" -> "{n, plural, =0{No hay mensajes en cola} =1{1 mensaje en cola} other{# mensajes en cola}}",
      "chat.queuePaused" -> "En pausa", "chat.resume" -> "Reanudar", "chat.addToQueue" -> "Añadir a la cola",
      "chat.edit" -> "Editar", "chat.remove" -> "Quitar", "chat.files" -> "{n, plural, =1{1 archivo} other{# archivos}}",
      "chat.queuedItem" -> "Mensaje en cola {i} de {n}: {text}",
      "chat.queuedHelp" -> "Intro para editarlo, Supr para quitarlo, Alt+Arriba o Alt+Abajo para moverlo",
      "chat.moved" -> "Movido a {i} de {n}", "chat.reorder" -> "Arrastra para reordenar",
      "chat.sendNext" -> "Enviar el siguiente", "chat.clearQueue" -> "Vaciar la cola",
      "chat.queueMore" -> "Más acciones de la cola",
      "tabs.maximize" -> "Ocultar los demás grupos", "tabs.unmaximize" -> "Mostrar todos los grupos"),
    "fr" -> Map("ok" -> "OK", "cancel" -> "Annuler", "close" -> "Fermer", "done" -> "Terminé", "yes" -> "Oui",
      "no" -> "Non", "save" -> "Enregistrer", "discard" -> "Ne pas enregistrer", "delete" -> "Supprimer",
      "retry" -> "Réessayer", "search" -> "Rechercher", "noResults" -> "Aucun résultat", "loading" -> "Chargement…",
      "back" -> "Retour", "apply" -> "Appliquer", "clear" -> "Effacer",
      "larger" -> "Plus grand", "smaller" -> "Plus petit", "defaultSize" -> "Taille par défaut",
      "leave" -> "Quitter", "stay" -> "Rester", "leaveTitle" -> "Quitter l'application ?",
      "leaveMessage" -> "Les modifications que vous avez apportées ne seront peut-être pas enregistrées.",
      "chat.queued" -> "{n, plural, =0{Aucun message en attente} =1{1 message en attente} other{# messages en attente}}",
      "chat.queuePaused" -> "En pause", "chat.resume" -> "Reprendre", "chat.addToQueue" -> "Ajouter à la file d'attente",
      "chat.edit" -> "Modifier", "chat.remove" -> "Retirer",
      "chat.files" -> "{n, plural, =1{1 fichier} other{# fichiers}}",
      "chat.queuedItem" -> "Message en attente {i} sur {n} : {text}",
      "chat.queuedHelp" -> "Entrée pour le modifier, Suppr pour le retirer, Alt+Haut ou Alt+Bas pour le déplacer",
      "chat.moved" -> "Déplacé en position {i} sur {n}", "chat.reorder" -> "Faites glisser pour réorganiser",
      "chat.sendNext" -> "Envoyer le suivant", "chat.clearQueue" -> "Vider la file d'attente",
      "chat.queueMore" -> "Autres actions de la file d'attente",
      "tabs.maximize" -> "Masquer les autres groupes", "tabs.unmaximize" -> "Afficher tous les groupes"),
    "it" -> Map("ok" -> "OK", "cancel" -> "Annulla", "close" -> "Chiudi", "done" -> "Fine", "yes" -> "Sì", "no" -> "No",
      "save" -> "Salva", "discard" -> "Non salvare", "delete" -> "Elimina", "retry" -> "Riprova", "search" -> "Cerca",
      "noResults" -> "Nessun risultato", "loading" -> "Caricamento…", "back" -> "Indietro", "apply" -> "Applica",
      "clear" -> "Cancella",
      "larger" -> "Più grande", "smaller" -> "Più piccolo", "defaultSize" -> "Dimensione predefinita",
      "leave" -> "Esci", "stay" -> "Resta", "leaveTitle" -> "Uscire dall'app?",
      "leaveMessage" -> "Le modifiche apportate potrebbero non essere salvate.",
      "chat.queued" -> "{n, plural, =0{Nessun messaggio in coda} =1{1 messaggio in coda} other{# messaggi in coda}}",
      "chat.queuePaused" -> "In pausa", "chat.resume" -> "Riprendi", "chat.addToQueue" -> "Aggiungi alla coda",
      "chat.edit" -> "Modifica", "chat.remove" -> "Rimuovi", "chat.files" -> "{n, plural, =1{1 file} other{# file}}",
      "chat.queuedItem" -> "Messaggio in coda {i} di {n}: {text}",
      "chat.queuedHelp" -> "Invio per modificarlo, Canc per rimuoverlo, Alt+Su o Alt+Giù per spostarlo",
      "chat.moved" -> "Spostato in posizione {i} di {n}", "chat.reorder" -> "Trascina per riordinare",
      "chat.sendNext" -> "Invia il successivo", "chat.clearQueue" -> "Svuota la coda",
      "chat.queueMore" -> "Altre azioni della coda",
      "tabs.maximize" -> "Nascondi gli altri gruppi", "tabs.unmaximize" -> "Mostra tutti i gruppi"),
    "nl" -> Map("ok" -> "OK", "cancel" -> "Annuleren", "close" -> "Sluiten", "done" -> "Klaar", "yes" -> "Ja",
      "no" -> "Nee", "save" -> "Opslaan", "discard" -> "Niet opslaan", "delete" -> "Verwijderen",
      "retry" -> "Opnieuw proberen", "search" -> "Zoeken", "noResults" -> "Geen resultaten", "loading" -> "Laden…",
      "back" -> "Terug", "apply" -> "Toepassen", "clear" -> "Wissen",
      "larger" -> "Groter", "smaller" -> "Kleiner", "defaultSize" -> "Standaardgrootte",
      "leave" -> "Verlaten", "stay" -> "Blijven", "leaveTitle" -> "App verlaten?",
      "leaveMessage" -> "Wijzigingen die je hebt aangebracht, worden mogelijk niet opgeslagen.",
      "chat.queued" -> ("{n, plural, =0{Geen berichten in de wachtrij} =1{1 bericht in de wachtrij} " +
        "other{# berichten in de wachtrij}}"),
      "chat.queuePaused" -> "Gepauzeerd", "chat.resume" -> "Hervatten", "chat.addToQueue" -> "Aan wachtrij toevoegen",
      "chat.edit" -> "Bewerken", "chat.remove" -> "Verwijderen",
      "chat.files" -> "{n, plural, =1{1 bestand} other{# bestanden}}",
      "chat.queuedItem" -> "Bericht {i} van {n} in de wachtrij: {text}",
      "chat.queuedHelp" -> "Enter om te bewerken, Delete om te verwijderen, Alt+Omhoog of Alt+Omlaag om te verplaatsen",
      "chat.moved" -> "Verplaatst naar {i} van {n}", "chat.reorder" -> "Sleep om te herschikken",
      "chat.sendNext" -> "Volgende verzenden", "chat.clearQueue" -> "Wachtrij wissen",
      "chat.queueMore" -> "Meer wachtrijacties",
      "tabs.maximize" -> "Andere groepen verbergen", "tabs.unmaximize" -> "Alle groepen tonen"),
    "pt" -> Map("ok" -> "OK", "cancel" -> "Cancelar", "close" -> "Fechar", "done" -> "Concluído", "yes" -> "Sim",
      "no" -> "Não", "save" -> "Salvar", "discard" -> "Não salvar", "delete" -> "Excluir", "retry" -> "Tentar novamente",
      "search" -> "Pesquisar", "noResults" -> "Nenhum resultado", "loading" -> "Carregando…", "back" -> "Voltar",
      "apply" -> "Aplicar", "clear" -> "Limpar",
      "larger" -> "Maior", "smaller" -> "Menor", "defaultSize" -> "Tamanho padrão",
      "leave" -> "Sair", "stay" -> "Ficar", "leaveTitle" -> "Sair do app?",
      "leaveMessage" -> "As alterações feitas talvez não sejam salvas.",
      "chat.queued" -> "{n, plural, =0{Nenhuma mensagem na fila} =1{1 mensagem na fila} other{# mensagens na fila}}",
      "chat.queuePaused" -> "Pausado", "chat.resume" -> "Retomar", "chat.addToQueue" -> "Adicionar à fila",
      "chat.edit" -> "Editar", "chat.remove" -> "Remover", "chat.files" -> "{n, plural, =1{1 arquivo} other{# arquivos}}",
      "chat.queuedItem" -> "Mensagem na fila {i} de {n}: {text}",
      "chat.queuedHelp" -> "Enter para editar, Delete para remover, Alt+Seta para cima ou para baixo para mover",
      "chat.moved" -> "Movido para {i} de {n}", "chat.reorder" -> "Arraste para reordenar",
      "chat.sendNext" -> "Enviar a próxima", "chat.clearQueue" -> "Limpar fila",
      "chat.queueMore" -> "Mais ações da fila",
      "tabs.maximize" -> "Ocultar os outros grupos", "tabs.unmaximize" -> "Mostrar todos os grupos"),
    "tr" -> Map("ok" -> "Tamam", "cancel" -> "İptal", "close" -> "Kapat", "done" -> "Bitti", "yes" -> "Evet",
      "no" -> "Hayır", "save" -> "Kaydet", "discard" -> "Kaydetme", "delete" -> "Sil", "retry" -> "Yeniden dene",
      "search" -> "Ara", "noResults" -> "Sonuç yok", "loading" -> "Yükleniyor…", "back" -> "Geri", "apply" -> "Uygula",
      "clear" -> "Temizle",
      "larger" -> "Daha büyük", "smaller" -> "Daha küçük", "defaultSize" -> "Varsayılan boyut",
      "leave" -> "Ayrıl", "stay" -> "Kal", "leaveTitle" -> "Uygulamadan ayrılınsın mı?",
      "leaveMessage" -> "Yaptığınız değişiklikler kaydedilmeyebilir.",
      "chat.queued" -> "{n, plural, =0{Sırada mesaj yok} other{Sırada # mesaj}}",
      "chat.queuePaused" -> "Duraklatıldı", "chat.resume" -> "Devam et", "chat.addToQueue" -> "Sıraya ekle",
      "chat.edit" -> "Düzenle", "chat.remove" -> "Kaldır", "chat.files" -> "{n, plural, other{# dosya}}",
      "chat.queuedItem" -> "Sıradaki mesaj {i}/{n}: {text}",
      "chat.queuedHelp" -> "Düzenlemek için Enter, kaldırmak için Delete, taşımak için Alt+Yukarı veya Alt+Aşağı",
      "chat.moved" -> "{i}/{n} konumuna taşındı", "chat.reorder" -> "Yeniden sıralamak için sürükleyin",
      "chat.sendNext" -> "Sıradakini gönder", "chat.clearQueue" -> "Sırayı temizle",
      "chat.queueMore" -> "Diğer sıra işlemleri",
      "tabs.maximize" -> "Diğer grupları gizle", "tabs.unmaximize" -> "Tüm grupları göster"),
    "ar" -> Map("ok" -> "موافق", "cancel" -> "إلغاء", "close" -> "إغلاق", "done" -> "تم", "yes" -> "نعم", "no" -> "لا",
      "save" -> "حفظ", "discard" -> "عدم الحفظ", "delete" -> "حذف", "retry" -> "إعادة المحاولة", "search" -> "بحث",
      "noResults" -> "لا توجد نتائج", "loading" -> "جارٍ التحميل…", "back" -> "رجوع", "apply" -> "تطبيق", "clear" -> "مسح",
      "larger" -> "أكبر", "smaller" -> "أصغر", "defaultSize" -> "الحجم الافتراضي",
      "leave" -> "مغادرة", "stay" -> "البقاء", "leaveTitle" -> "هل تريد مغادرة التطبيق؟",
      "leaveMessage" -> "قد لا يتم حفظ التغييرات التي أجريتها.",
      "chat.queued" -> ("{n, plural, =0{لا توجد رسائل في قائمة الانتظار} =1{رسالة واحدة في قائمة الانتظار} " +
        "=2{رسالتان في قائمة الانتظار} other{# رسائل في قائمة الانتظار}}"),
      "chat.queuePaused" -> "متوقف مؤقتًا", "chat.resume" -> "استئناف", "chat.addToQueue" -> "إضافة إلى قائمة الانتظار",
      "chat.edit" -> "تعديل", "chat.remove" -> "إزالة", "chat.files" -> "{n, plural, =1{ملف واحد} =2{ملفان} other{# ملفات}}",
      "chat.queuedItem" -> "رسالة في قائمة الانتظار {i} من {n}: {text}",
      "chat.queuedHelp" -> "Enter للتعديل، وDelete للإزالة، وAlt مع السهم لأعلى أو لأسفل للنقل",
      "chat.moved" -> "نُقلت إلى {i} من {n}", "chat.reorder" -> "اسحب لإعادة الترتيب",
      "chat.sendNext" -> "إرسال التالية", "chat.clearQueue" -> "مسح قائمة الانتظار",
      "chat.queueMore" -> "مزيد من إجراءات قائمة الانتظار",
      "tabs.maximize" -> "إخفاء المجموعات الأخرى", "tabs.unmaximize" -> "إظهار كل المجموعات"),
    "zh" -> Map("ok" -> "确定", "cancel" -> "取消", "close" -> "关闭", "done" -> "完成", "yes" -> "是", "no" -> "否",
      "save" -> "保存", "discard" -> "不保存", "delete" -> "删除", "retry" -> "重试", "search" -> "搜索",
      "noResults" -> "无结果", "loading" -> "正在加载…", "back" -> "返回", "apply" -> "应用", "clear" -> "清除",
      "larger" -> "放大", "smaller" -> "缩小", "defaultSize" -> "默认大小",
      "leave" -> "离开", "stay" -> "留下", "leaveTitle" -> "要离开应用吗？",
      "leaveMessage" -> "系统可能不会保存您所做的更改。",
      "chat.queued" -> "{n, plural, =0{没有排队的消息} other{# 条消息正在排队}}",
      "chat.queuePaused" -> "已暂停", "chat.resume" -> "继续", "chat.addToQueue" -> "加入队列",
      "chat.edit" -> "编辑", "chat.remove" -> "移除", "chat.files" -> "{n, plural, other{# 个文件}}",
      "chat.queuedItem" -> "排队消息 {i}/{n}：{text}",
      "chat.queuedHelp" -> "按 Enter 编辑，按 Delete 移除，按 Alt+上或 Alt+下 移动",
      "chat.moved" -> "已移至第 {i} 个，共 {n} 个", "chat.reorder" -> "拖动以重新排序",
      "chat.sendNext" -> "发送下一条", "chat.clearQueue" -> "清空队列", "chat.queueMore" -> "更多队列操作",
      "tabs.maximize" -> "隐藏其他组", "tabs.unmaximize" -> "显示所有组"),
    "zh_Hant" -> Map("ok" -> "確定", "cancel" -> "取消", "close" -> "關閉", "done" -> "完成", "yes" -> "是", "no" -> "否",
      "save" -> "儲存", "discard" -> "不儲存", "delete" -> "刪除", "retry" -> "重試", "search" -> "搜尋",
      "noResults" -> "沒有結果", "loading" -> "正在載入…", "back" -> "返回", "apply" -> "套用", "clear" -> "清除",
      "larger" -> "放大", "smaller" -> "縮小", "defaultSize" -> "預設大小",
      "leave" -> "離開", "stay" -> "留下", "leaveTitle" -> "要離開應用程式嗎？",
      "leaveMessage" -> "系統可能不會儲存你所做的變更。",
      "chat.queued" -> "{n, plural, =0{沒有排隊的訊息} other{# 則訊息正在排隊}}",
      "chat.queuePaused" -> "已暫停", "chat.resume" -> "繼續", "chat.addToQueue" -> "加入佇列",
      "chat.edit" -> "編輯", "chat.remove" -> "移除", "chat.files" -> "{n, plural, other{# 個檔案}}",
      "chat.queuedItem" -> "排隊訊息 {i}/{n}：{text}",
      "chat.queuedHelp" -> "按 Enter 編輯，按 Delete 移除，按 Alt+上或 Alt+下 移動",
      "chat.moved" -> "已移至第 {i} 個，共 {n} 個", "chat.reorder" -> "拖曳以重新排序",
      "chat.sendNext" -> "傳送下一則", "chat.clearQueue" -> "清除佇列", "chat.queueMore" -> "更多佇列動作",
      "tabs.maximize" -> "隱藏其他群組", "tabs.unmaximize" -> "顯示所有群組")))

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
