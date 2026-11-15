package everyui.tray

import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters.*

/** The Objective-C runtime, AppKit and libdispatch on macOS, reached through the FFM API (no JNI, no library of
  * EveryUI's own), for the tray (`MacTray`): classes and selectors by name, messages sent, a class of the app's own made
  * at run time, blocks, and work handed to the main thread, where AppKit is to be spoken to.
  *
  * A message is sent through `objc_msgSend` as the function its method is, one shape of call to each of its
  * signatures (the `FunctionDescriptor`s below, every one listed in `downcalls`), which is right on Apple silicon,
  * where `objc_msgSend` is not variadic. A method that returns nothing is sent as one that returns a pointer, which is
  * not read. Every shape and every call back is listed (`downcalls`, `upcalls`) as a native image's metadata names
  * them (META-INF/native-image/com.outr/everyui-desktop-tray-macos). */
private[tray] object ObjC {
  private val P = ValueLayout.ADDRESS
  private val J = ValueLayout.JAVA_LONG
  private val Z = ValueLayout.JAVA_BOOLEAN
  private val D = ValueLayout.JAVA_DOUBLE
  /** An NSSize: width and height, in points. */
  val Size: StructLayout = MemoryLayout.structLayout(D, D)
  /** An NSRect: x, y (from the lower left), width and height, in points. */
  val Rect: StructLayout = MemoryLayout.structLayout(D, D, D, D)

  // objc_msgSend, by what follows the receiver and selector
  val Send: FunctionDescriptor = FunctionDescriptor.of(P, P, P)
  val SendP: FunctionDescriptor = FunctionDescriptor.of(P, P, P, P)
  val SendPP: FunctionDescriptor = FunctionDescriptor.of(P, P, P, P, P)
  val SendPPP: FunctionDescriptor = FunctionDescriptor.of(P, P, P, P, P, P)
  val SendPPPP: FunctionDescriptor = FunctionDescriptor.of(P, P, P, P, P, P, P)
  val SendPPJ: FunctionDescriptor = FunctionDescriptor.of(P, P, P, P, P, J)
  val SendPPPJ: FunctionDescriptor = FunctionDescriptor.of(P, P, P, P, P, P, J)
  val SendJ: FunctionDescriptor = FunctionDescriptor.of(P, P, P, J)
  val SendJP: FunctionDescriptor = FunctionDescriptor.of(P, P, P, J, P)
  val SendZ: FunctionDescriptor = FunctionDescriptor.of(P, P, P, Z)
  val SendD: FunctionDescriptor = FunctionDescriptor.of(P, P, P, D)
  val SendSize: FunctionDescriptor = FunctionDescriptor.of(P, P, P, Size)
  val GetLong: FunctionDescriptor = FunctionDescriptor.of(J, P, P)
  val GetRect: FunctionDescriptor = FunctionDescriptor.of(Rect, P, P)
  /** NSBitmapImageRep's initWithBitmapDataPlanes:pixelsWide:pixelsHigh:bitsPerSample:samplesPerPixel:hasAlpha:
    * isPlanar:colorSpaceName:bitmapFormat:bytesPerRow:bitsPerPixel:. */
  val InitBitmap: FunctionDescriptor = FunctionDescriptor.of(P, P, P, P, J, J, J, J, Z, Z, P, J, J, J)
  // the runtime's and libdispatch's own
  val Named: FunctionDescriptor = FunctionDescriptor.of(P, P)              // objc_getClass, sel_registerName, ...
  val AllocClass: FunctionDescriptor = FunctionDescriptor.of(P, P, P, J)   // objc_allocateClassPair
  val AddMethod: FunctionDescriptor = FunctionDescriptor.of(Z, P, P, P, P) // class_addMethod
  val AddProtocol: FunctionDescriptor = FunctionDescriptor.of(Z, P, P)     // class_addProtocol
  val Push: FunctionDescriptor = FunctionDescriptor.of(P)                  // objc_autoreleasePoolPush
  val Async: FunctionDescriptor = FunctionDescriptor.ofVoid(P, P, P)       // dispatch_async_f
  /** One pointer in, nothing back: objc_registerClassPair, objc_autoreleasePoolPop, a block of no arguments invoked;
    * and called back, the work handed to the main thread. */
  val Void1: FunctionDescriptor = FunctionDescriptor.ofVoid(P)
  /** A block invoked with an NSUInteger. */
  val BlockLong: FunctionDescriptor = FunctionDescriptor.ofVoid(P, J)
  // called back: a method of one object argument (`- (void)clicked:(id)sender`), a notification centre delegate's
  // methods (centre, notification or response, completion handler), and the block told whether notifications may show
  val Method: FunctionDescriptor = FunctionDescriptor.ofVoid(P, P, P)
  val Method3: FunctionDescriptor = FunctionDescriptor.ofVoid(P, P, P, P, P)
  val Granted: FunctionDescriptor = FunctionDescriptor.ofVoid(P, Z, P)

  /** Every function called, by its shape, as a native image is to be told of them. */
  val downcalls: List[FunctionDescriptor] = List(Send, SendP, SendPP, SendPPP, SendPPPP, SendPPJ, SendPPPJ, SendJ, SendJP,
    SendZ, SendD, SendSize, GetLong, GetRect, InitBitmap, Named, AllocClass, AddMethod, AddProtocol, Push, Async, Void1,
    BlockLong)
  /** Every function called back, by its shape. */
  val upcalls: List[FunctionDescriptor] = List(Void1, Method, Method3, Granted)

  val NULL: MemorySegment = MemorySegment.NULL

  private lazy val linker = Linker.nativeLinker()
  private lazy val objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
  private lazy val system = SymbolLookup.libraryLookup("/usr/lib/libSystem.B.dylib", Arena.global())
  private def symbol(in: SymbolLookup, name: String): MemorySegment =
    in.find(name).orElseThrow(() => new UnsatisfiedLinkError(name))
  private def function(in: SymbolLookup, name: String, d: FunctionDescriptor): MethodHandle =
    linker.downcallHandle(symbol(in, name), d)

  private lazy val msgSend = symbol(objc, "objc_msgSend")
  private val sends = new ConcurrentHashMap[FunctionDescriptor, MethodHandle]()
  private lazy val classNamed = function(objc, "objc_getClass", Named)
  private lazy val registerName = function(objc, "sel_registerName", Named)
  private lazy val getProtocol = function(objc, "objc_getProtocol", Named)
  private lazy val allocateClassPair = function(objc, "objc_allocateClassPair", AllocClass)
  private lazy val addMethod = function(objc, "class_addMethod", AddMethod)
  private lazy val addProtocol = function(objc, "class_addProtocol", AddProtocol)
  private lazy val registerClassPair = function(objc, "objc_registerClassPair", Void1)
  private lazy val poolPush = function(objc, "objc_autoreleasePoolPush", Push)
  private lazy val poolPop = function(objc, "objc_autoreleasePoolPop", Void1)
  private lazy val dispatchAsync = function(system, "dispatch_async_f", Async)
  private lazy val mainQueue = symbol(system, "_dispatch_main_q")
  private lazy val globalBlock = symbol(system, "_NSConcreteGlobalBlock")
  private lazy val callBlock = linker.downcallHandle(Void1)
  private lazy val callBlockLong = linker.downcallHandle(BlockLong)

  private def invoke(h: MethodHandle, args: Any*): AnyRef = h.invokeWithArguments(args.map(_.asInstanceOf[AnyRef]).asJava)

  /** A framework's code loaded (AppKit, UserNotifications), so its classes are known to the runtime. */
  def load(framework: String): Unit =
    SymbolLookup.libraryLookup("/System/Library/Frameworks/" + framework + ".framework/" + framework, Arena.global())

  private val classes = new ConcurrentHashMap[String, MemorySegment]()
  private val selectors = new ConcurrentHashMap[String, MemorySegment]()

  private def named(h: MethodHandle, name: String): MemorySegment = {
    val a = Arena.ofConfined()
    try invoke(h, a.allocateFrom(name)).asInstanceOf[MemorySegment] finally a.close()
  }

  /** The class `name`, NULL where there is none (its framework not loaded). */
  def cls(name: String): MemorySegment = {
    val known = classes.get(name)
    if (known != null) known else {
      val c = named(classNamed, name)
      if (c.address != 0L) classes.put(name, c)
      c
    }
  }

  /** The selector `name` ("setTitle:"). */
  def sel(name: String): MemorySegment = selectors.computeIfAbsent(name, n => named(registerName, n))

  private def handle(d: FunctionDescriptor): MethodHandle = sends.computeIfAbsent(d, s => linker.downcallHandle(msgSend, s))

  /** `selector` sent to `receiver` as a method of shape `d`, with `args`; what it gives back, boxed. */
  def send(d: FunctionDescriptor, receiver: MemorySegment, selector: String, args: Any*): AnyRef =
    invoke(handle(d), (receiver +: sel(selector) +: args)*)

  /** `selector` sent with no arguments but objects (none to four), for the object it gives back (or nothing). */
  def id(receiver: MemorySegment, selector: String, args: MemorySegment*): MemorySegment = {
    val d = args.size match {
      case 0 => Send
      case 1 => SendP
      case 2 => SendPP
      case 3 => SendPPP
      case _ => SendPPPP
    }
    send(d, receiver, selector, args*).asInstanceOf[MemorySegment]
  }

  /** `selector` sent with an integer (an NSInteger or NSUInteger). */
  def withLong(receiver: MemorySegment, selector: String, v: Long): MemorySegment =
    send(SendJ, receiver, selector, v).asInstanceOf[MemorySegment]

  /** `selector` sent with a BOOL. */
  def withBool(receiver: MemorySegment, selector: String, v: Boolean): MemorySegment =
    send(SendZ, receiver, selector, v).asInstanceOf[MemorySegment]

  /** `selector` sent for an integer back. */
  def long(receiver: MemorySegment, selector: String): Long = send(GetLong, receiver, selector).asInstanceOf[Long]

  /** `selector` sent for an NSRect back: x, y (from the lower left), width, height. */
  def rect(receiver: MemorySegment, selector: String): Array[Double] = {
    val a = Arena.ofConfined()
    try {
      val r = invoke(handle(GetRect), a, receiver, sel(selector)).asInstanceOf[MemorySegment]
      Array.tabulate(4)(i => r.getAtIndex(ValueLayout.JAVA_DOUBLE, i.toLong))
    }
    finally a.close()
  }

  /** `selector` sent with an NSSize. */
  def withSize(receiver: MemorySegment, selector: String, w: Double, h: Double): MemorySegment = {
    val a = Arena.ofConfined()
    try {
      val s = a.allocate(Size)
      s.setAtIndex(ValueLayout.JAVA_DOUBLE, 0L, w)
      s.setAtIndex(ValueLayout.JAVA_DOUBLE, 1L, h)
      send(SendSize, receiver, selector, s).asInstanceOf[MemorySegment]
    }
    finally a.close()
  }

  def isNil(o: MemorySegment): Boolean = o == null || o.address == 0L

  /** A new one of `cls`, made with `init` (+1: `release` it). */
  def make(cls: String): MemorySegment = id(id(this.cls(cls), "alloc"), "init")
  def retain(o: MemorySegment): MemorySegment = if (isNil(o)) o else id(o, "retain")
  def release(o: MemorySegment): Unit = if (!isNil(o)) id(o, "release")

  /** An NSString of `s` (autoreleased). */
  def str(s: String): MemorySegment = {
    val a = Arena.ofConfined()
    try id(cls("NSString"), "stringWithUTF8String:", a.allocateFrom(s)) finally a.close()
  }

  /** An NSString's words; "" for nil. */
  def text(ns: MemorySegment): String =
    if (isNil(ns)) "" else {
      val c = id(ns, "UTF8String")
      if (isNil(c)) "" else c.reinterpret(Long.MaxValue).getString(0L)
    }

  /** An NSMutableArray of `items` (autoreleased). */
  def array(items: MemorySegment*): MemorySegment = {
    val a = id(cls("NSMutableArray"), "array")
    items.foreach(i => id(a, "addObject:", i))
    a
  }

  /** `f` with a pool of its own, what it autoreleased released after it. */
  def pooled[A](f: => A): A = {
    val pool = invoke(poolPush).asInstanceOf[MemorySegment]
    try f finally invoke(poolPop, pool)
  }

  /** A call back of shape `d` into `target`, for as long as the process lives. */
  def upcall(target: MethodHandle, d: FunctionDescriptor): MemorySegment = linker.upcallStub(target, d, Arena.global())

  /** A class of the app's own, made once: NSObject's, with `methods` (selector, type encoding, implementation) and
    * conforming to `protocols`; the one made before where it is there already. */
  def subclass(name: String, methods: List[(String, String, MemorySegment)], protocols: List[String] = Nil): MemorySegment =
    synchronized {
      val known = cls(name)
      if (!isNil(known)) known else {
        val made = invoke(allocateClassPair, cls("NSObject"), Arena.global().allocateFrom(name), 0L)
          .asInstanceOf[MemorySegment]
        methods.foreach { case (s, types, imp) => invoke(addMethod, made, sel(s), imp, Arena.global().allocateFrom(types)) }
        protocols.foreach { p =>
          val proto = named(getProtocol, p)
          if (!isNil(proto)) invoke(addProtocol, made, proto)
        }
        invoke(registerClassPair, made)
        classes.put(name, made)
        made
      }
    }

  /** A global block (one that captures nothing, never copied or freed) whose code is `invoke`, an upcall taking the
    * block first. */
  def block(invoke: MemorySegment): MemorySegment = {
    val descriptor = Arena.global().allocate(16L, 8L)
    descriptor.set(ValueLayout.JAVA_LONG, 0L, 0L)
    descriptor.set(ValueLayout.JAVA_LONG, 8L, 32L)
    val b = Arena.global().allocate(32L, 8L)
    b.set(P, 0L, globalBlock)
    b.set(ValueLayout.JAVA_INT, 8L, 1 << 28)   // BLOCK_IS_GLOBAL
    b.set(ValueLayout.JAVA_INT, 12L, 0)
    b.set(P, 16L, invoke)
    b.set(P, 24L, descriptor)
    b
  }

  private def code(block: MemorySegment): MemorySegment = block.reinterpret(32L).get(P, 16L)

  /** A block handed to the app (a completion handler) called with no arguments. */
  def callBack(block: MemorySegment): Unit = if (!isNil(block)) invoke(callBlock, code(block), block)

  /** A block handed to the app called with an NSUInteger. */
  def callBack(block: MemorySegment, v: Long): Unit = if (!isNil(block)) invoke(callBlockLong, code(block), block, v)

  private val jobs = new ConcurrentHashMap[Long, () => Unit]()
  private val numbered = new AtomicLong(0L)

  /** Run as libdispatch hands it back, on the main thread. */
  def work(ctx: MemorySegment): Unit =
    try {
      val job = jobs.remove(ctx.address)
      if (job != null) pooled(job())
    }
    catch { case t: Throwable => everyui.scribe.warn("tray: on the main thread: " + t) }

  private lazy val worker = upcall(java.lang.invoke.MethodHandles.lookup().findVirtual(getClass, "work",
    java.lang.invoke.MethodType.methodType(Void.TYPE, classOf[MemorySegment])).bindTo(this), Void1)

  /** `f` run on the main thread, where AppKit is to be spoken to, in its turn after what was handed there before: the
    * main queue, which the run loop JavaFX runs there drains. */
  def main(f: => Unit): Unit = {
    val n = numbered.incrementAndGet()
    jobs.put(n, () => f)
    invoke(dispatchAsync, mainQueue, MemorySegment.ofAddress(n), worker)
  }
}
