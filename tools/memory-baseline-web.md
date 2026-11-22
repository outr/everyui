# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left | nodes made per tick | aria writes per tick |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| TextView | view | 3514 | 3416 | 2.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| TextView, ticking | view | 3827 | 3349 | 2.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| Button | button | 11706 | 7693 | 3.0 | 5.0 | 2.0 | 0.0 | 0.0 | 0.0 |
| Container | container | 3263 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| ImageView | view | 3003 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 | 0.0 | 0.0 |
| ScrollContainer | scroll | 12868 | 9884 | 4.0 | 1.0 | 3.0 | 0.01 | 0.0 | 0.0 |
| VideoView, playing a clip | video | 43777 | 58590 | 148.0 | 11.0 | 1.0 | 0.13 | 0.0 | 0.0 |
| ScrollContainer of cards | card | 14589 | 15727 | 7.0 | 1.0 | 4.0 | 0.01 | 0.0 | 0.0 |
| VirtualList of 1,000 rows | list | 556737 | 243910 | 107.0 | 7.0 | 81.0 | 0.13 | 0.0 | 0.0 |
| Home of 22 rails of 10 cards, all made | home | 1518422 | 1086768 | 706.0 | 127.0 | 486.0 | 0.25 | 0.0 | 0.0 |
| Home of 22 rails of 10 cards, made near the view | home | 350646 | 215480 | 123.0 | 7.0 | 87.0 | 0.25 | 0.0 | 0.0 |
| DataTable of 100 rows | table | 965827 | 454354 | 284.0 | 154.0 | 148.0 | 0.13 | 0.0 | 0.0 |
| DataTable of 100 rows in a page | table | 979683 | 393648 | 328.0 | 175.0 | 88.0 | 0.13 | 0.0 | 0.0 |
| TabGroups | tab group | 90873 | 36645 | 25.0 | 14.0 | 20.0 | 0.05 | 0.0 | 0.0 |
| TabGroups, tabs not shown | hidden tab | 170103 | 49837 | 64.0 | 26.7 | 38.0 | 0.05 | 0.0 | 0.0 |
| Transcript, plain | message | 61507 | 20697 | 18.02 | 2.0 | 13.0 | 0.01 | 0.0 | 0.0 |
| Transcript, Markdown paragraph | message | 66132 | 32579 | 30.02 | 8.0 | 13.0 | 0.01 | 0.0 | 0.0 |
| Transcript, code block of 40 lines | message | 161424 | 429199 | 743.1 | 8.0 | 19.0 | 0.03 | 0.0 | 0.0 |
| Transcript, Markdown table | message | 220651 | 133010 | 100.1 | 5.0 | 49.0 | 0.03 | 0.0 | 0.0 |
| Transcript, tool call folded | message | 64413 | 32437 | 21.0 | 7.0 | 16.0 | 0.01 | 0.0 | 0.0 |
| Transcript, tool call open | message | 86128 | 49293 | 31.0 | 7.0 | 22.0 | 0.01 | 0.0 | 0.0 |
| Transcript, tool call running | message | 78979 | 39734 | 29.0 | 7.0 | 16.0 | 0.03 | 0.0 | 0.0 |
| Transcript in 900 px, plain | message | 6452 | 618 | 1.02 | 0.14 | 0.0 | 0.0 | 0.0 | 0.0 |
| BarChart, 7 days by 3 series | chart | 183411 | 99202 | 64.0 | 8.0 | 25.0 | 0.05 | 0.0 | 0.0 |
| LineChart, 30 days | chart | 256754 | 105429 | 77.0 | 8.0 | 38.0 | 0.05 | 0.0 | 0.0 |
| DonutChart, 5 parts | chart | 199744 | 102998 | 77.0 | 8.0 | 48.0 | 0.05 | 0.0 | 0.0 |
| Heatmap, 7 by 24 | chart | 388485 | 317008 | 246.0 | 8.0 | 38.0 | 0.13 | 0.0 | 0.0 |
| StatTile with a trend | tile | 40851 | 25704 | 16.0 | 0.0 | 8.0 | 0.03 | 0.0 | 0.0 |
| Meter | meter | 25530 | 14060 | 7.0 | 0.0 | 5.0 | 0.03 | 0.0 | 0.0 |
