# RsPaint Extended
RsPaint Extended lets you draw on the ground in RuneLite. It is based on [RsPaint](https://github.com/geheur/better-ground-markers-improved) by geheur.

To draw, press the draw key (default `ctrl-b`) to turn on draw mode. Then click and drag the mouse. To turn off draw mode, press the draw key again.

To erase, press the erase key (default `ctrl-e`) to turn on erase mode. Then click and drag the mouse over a line. To turn off erase mode, press the erase key again.

To undo your last line or erase, press the undo key (default `ctrl-z`) while draw mode or erase mode is on. To redo it, press the redo key (default `ctrl-y`). One erase drag undoes as one change. Undo goes back 100 changes. If you are in a party, your party members see the undo and the redo too. Reset canvas deletes the undo history.

Draw mode and erase mode cannot both be on. If you press the erase key in draw mode, erase mode replaces draw mode. The opposite is also true. While one of the modes is on, a left click draws or erases, and does not move your character.

To change the color, use the color buttons on the left side of the screen. The buttons show only while draw mode or erase mode is on.

If you log out or change worlds, your drawings stay. If you close the client, your drawings are deleted. To keep them, export them to a file.

## Party drawings
If you are in a party, your party members see your drawings while you draw. You see their drawings too. Each member has a separate canvas, and you can erase only your own strokes. To join a party, use the Party plugin.

When a member joins the party, they get all of your drawings.

To open the RsPaint Extended panel, click the paintbrush icon in the side bar. The panel shows you and each party member.

- To hide or show the drawings of a player, click the switch next to the name.
- To delete all of your drawings, click Reset canvas. This deletes all of your drawings, for you and for your party members. You cannot undo this.

## Import and export drawings
You can save drawings to a JSON file and load them again later. Use the RsPaint Extended panel.

- To save the drawings of one player, click Export next to the name.
- To save the drawings of all players whose switch is on, click Export shown drawings. All of the drawings go into one file.
- To load drawings from a file, click Import drawings. The drawings are added to your own drawings. If you are in a party, your party members see them too.

Imported drawings become your strokes, so you can erase them. Import does not delete the drawings that you already have.

## Credits and license
geheur wrote the original plugin, [RsPaint](https://github.com/geheur/better-ground-markers-improved). RsPaint Extended adds party drawings, import and export, and more color and stroke settings.
