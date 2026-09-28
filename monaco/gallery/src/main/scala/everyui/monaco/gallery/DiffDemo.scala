package everyui.monaco.gallery

import everyui.monaco.*

/** The original against the modified, side by side or inline, switched live. */
object DiffDemo {
  val original: String =
    """class Counter {
      |  int count = 0;
      |
      |  void increment() {
      |    count += 1;
      |    print('count is now $count');
      |  }
      |
      |  void reset() {
      |    count = 0;
      |  }
      |}
      |""".stripMargin

  val modified: String =
    """class Counter {
      |  int _count = 0;
      |
      |  int get count => _count;
      |
      |  void increment([int by = 1]) {
      |    _count += by;
      |    print('count is now $_count');
      |  }
      |
      |  Future<void> reset() async {
      |    await Future<void>.delayed(const Duration(milliseconds: 50));
      |    _count = 0;
      |  }
      |}
      |""".stripMargin

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoDiffController(original, modified, "dart")
    val layout = new Picker(List("Side-by-side", "Inline"), "Side-by-side")(v => controller.setRenderSideBySide(v == "Side-by-side"))
    new DemoPage(demo, new MonacoDiffEditor(controller), List(layout), () => controller.dispose())
  }
}
