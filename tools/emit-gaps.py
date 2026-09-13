#!/usr/bin/env python3
"""Static gap meter for a emitted Roku channel.

Counts what the emitter could not translate, or translated into something that
cannot run:

  * RtTodo markers  — trees the emitter explicitly gave up on
  * dangling symbols  — functions called but never defined (app + runtime)
  * duplicate defs    — two functions with one name (BrightScript rejects it)
  * unknown methods   — dotted calls that are neither a BrightScript interface
                        method nor a vtable slot the emitter installs

A clean channel reads 0 on every line. It proves the output is self-consistent,
not that it behaves correctly — tools/roku-acceptance.sh does that.

    python3 tools/emit-gaps.py reactive-test/out-roku/components
"""
import collections
import io
import os
import re
import sys

d = sys.argv[1] if len(sys.argv) > 1 else "reactive/out-roku/components"
raw = io.open(os.path.join(d, "App.brs"), encoding="utf-8").read()
runtime = io.open(os.path.join(d, "ScalaRuntime.brs"), encoding="utf-8").read()
extra = os.path.join(d, "UniversalRuntime.brs")
if os.path.exists(extra):
    runtime += io.open(extra, encoding="utf-8").read()

# Scan CODE only: a string literal such as "Var(" is not a call.
code = re.sub(r'"(?:[^"\n]|"")*"', '""', raw)
code = re.sub(r"'[^\n]*", "", code)

defs_app = re.findall(r'^\s*(?:function|sub)\s+([A-Za-z_$][A-Za-z0-9_$]*)', code, re.M)
defined = {n.lower() for n in defs_app} | {
    n.lower() for n in re.findall(r'^\s*(?:function|sub)\s+([A-Za-z_$][A-Za-z0-9_$]*)', runtime, re.M)}

todos = collections.Counter(re.findall(r'RtTodo\("([A-Za-z ]+)"\)', raw))

keywords = {k.lower() for k in {
    "if", "while", "for", "return", "print", "and", "or", "not", "then", "end", "sub", "function",
    "invalid", "true", "false", "stop", "each", "to", "step", "as", "throw",
    "CreateObject", "GetGlobalAA", "GetInterface", "Type", "Box", "Chr", "Val", "Str", "Int", "Rnd",
    "Sqr", "Abs", "Sgn", "Cint", "Fix", "Log", "Exp", "Atn", "Sin", "Cos", "Tan", "Asc", "Left",
    "Right", "Mid", "Len", "Instr", "String", "StringI", "UCase", "LCase", "StrToI", "FormatJson",
    "ParseJson", "Wait", "Eval", "LINE_NUM"}}
called = collections.Counter(m.group(1) for m in re.finditer(r'(?<![.\w$])([A-Za-z_$][A-Za-z0-9_$]*)\s*\(', code))
dangling = sorted(n for n in called if n.lower() not in defined and n.lower() not in keywords)

dupes = sorted(n for n, c in collections.Counter(x.lower() for x in defs_app).items() if c > 1)

interface_methods = {m.lower() for m in {
    "count", "push", "pop", "peek", "delete", "clear", "append", "join", "split", "trim", "toStr", "toInt",
    "left", "right", "mid", "instr", "len", "replace", "startsWith", "endsWith", "tokenize", "keys",
    "items", "lookup", "doesExist", "setModeCaseSensitive", "sortBy", "sort", "reverse", "getField",
    "setField", "observeField", "callFunc", "findNode", "appendChild", "removeChild", "getChild",
    "getChildCount", "createChild", "update", "isEmpty", "addReplace", "getEntityEncode", "subtype",
    "setFocus", "initialValue", "closure_call"}}
slots = {s.lower() for s in re.findall(r'^\s*self\.([A-Za-z_][A-Za-z0-9_]*)\s*=\s*[A-Za-z_][A-Za-z0-9_]*\s*$', code, re.M)}
# `ga.<Module>.<member>(…)` is a facade namespace the runtime installs in the
# global assoc array, not an object method.
facade_free = re.sub(r'\bga\.[A-Za-z_][A-Za-z0-9_]*\.[A-Za-z_][A-Za-z0-9_]*\s*\(', 'ga_facade(', code)
# `cl.fn(cl.env, [...])` is a closure called in place (a Scala function value), not an object method.
facade_free = re.sub(r'\.fn\(([A-Za-z_][A-Za-z0-9_.]*)\.env\s*,', r'.closure_call(\1.env,', facade_free)
unknown = sorted({n for n in re.findall(r'\.([A-Za-z_][A-Za-z0-9_]*)\s*\(', facade_free)
                  if n.lower() not in interface_methods and n.lower() not in slots})

print("functions emitted : %d" % len(defs_app))
print("vtable slots      : %d" % len(slots))
print("RtTodo markers  : %d  %s" % (sum(todos.values()), dict(todos) if todos else ""))
print("dangling symbols  : %d  %s" % (len(dangling), dangling if dangling else ""))
print("duplicate defs    : %d  %s" % (len(dupes), dupes if dupes else ""))
print("unknown methods   : %d  %s" % (len(unknown), unknown if unknown else ""))
total = sum(todos.values()) + len(dangling) + len(dupes) + len(unknown)
print("TOTAL GAPS        : %d" % total)
sys.exit(0 if total == 0 else 1)
