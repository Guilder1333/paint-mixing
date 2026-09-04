Android App

User takes palette pictures using camera:

On palette picture can pick any amount of colors. Palette should just name them in order of picking and remember.
Save the picture with named colors and remember picked color codes (the best color encoding format)

After palette is created can take any amount of match target pictures.
On matching target need to pick only one point, which color to match to.

The app should look at palette and find the best colors to mix and which proportions to use to make matched color.

Current assumptions:
1. color mixing formula even in real world is predictable to some point.
2. I don't need high precision, so even approximate results are okay
3. Taking two picture without post-processing in the same conditions would give good enough understanding on which color would be in result
4. Even if there are no good algorithm for that, pure bruteforce with pairing each color to each (i.e. mixing only 2 colors) would provide good enough result

Output: picture of the palette with color names (as explained before) and 2 colors to mix and their proportions.

