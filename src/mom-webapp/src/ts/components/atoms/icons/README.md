# Icon credits

Icons by [swmansion](https://www.svgrepo.com/collection/duotone-basic-interface-icons/)
("Duotone Basic Interface Icons" collection on SVG Repo), licensed under
[CC Attribution](https://creativecommons.org/licenses/by/4.0/).

`StatsIcon.ts` is a modified version of that collection's "grid-2-vertical" icon:
the left bar's height was reduced to 2/3 of the right bar's to read as a small
bar chart.

`StatusPausedIcon.ts` combines that collection's "pause" icon (the two bars,
redrawn as filled shapes instead of strokes) with the other status icons' round
outline, to match their two-tone convention.

`DocumentCheckIcon.ts` is that collection's "document-check" icon, unmodified.

`StatusInProgressIcon.ts` combines that collection's "menu-horizontal" icon (the
three dots, redrawn as filled circles instead of strokes) with the other status
icons' round outline, like `StatusPausedIcon.ts`. The dots pulse one after the
other (see `style.scss`), unless the system asks for reduced motion.
