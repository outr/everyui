# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left |
|---|---|---:|---:|---:|---:|---:|---:|
| TextView | view | 9253 | 3774 | 2.0 | 0.0 | 1.0 | 0.0 |
| Button | button | 30554 | 8403 | 4.0 | 5.0 | 3.0 | 0.0 |
| Container | container | 6044 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 |
| ImageView | view | 5418 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 |
| ScrollContainer | scroll | 23336 | 10326 | 4.0 | 1.0 | 3.0 | 0.01 |
| VirtualList of 1,000 rows | list | 1500926 | 387680 | 185.0 | 7.0 | 159.0 | 0.13 |
| DataTable of 100 rows | table | 6569503 | 2289352 | 1803.0 | 549.0 | 684.0 | 0.13 |
| TabGroups | tab group | 152921 | 37984 | 25.0 | 14.0 | 20.0 | 0.05 |
| TabGroups, tabs not shown | hidden tab | 355123 | 56686 | 64.0 | 26.0 | 38.0 | 0.05 |
| Transcript, plain | message | 123692 | 30829 | 23.02 | 2.0 | 16.0 | 0.01 |
| Transcript, Markdown paragraph | message | 129145 | 45144 | 47.02 | 10.0 | 16.0 | 0.01 |
| Transcript, code block of 40 lines | message | 230219 | 640837 | 985.1 | 8.0 | 20.0 | 0.03 |
| Transcript, Markdown table | message | 682324 | 262315 | 246.1 | 5.0 | 99.0 | 0.03 |
| Transcript, tool call folded | message | 117295 | 34323 | 21.0 | 7.0 | 16.0 | 1.0 |
| Transcript, tool call open | message | 167398 | 51757 | 31.0 | 7.0 | 22.0 | 1.01 |
| Transcript in 900 px, plain | message | 9671 | 1310 | 1.02 | 0.14 | 0.0 | 0.44 |
