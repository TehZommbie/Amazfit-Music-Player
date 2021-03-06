# (Original ReadMe)
## Amazfit-Music-Player

## Overview
I (3ddN) really like my Xiaomi Huami Amazfit Pace smartwatch and love the feature to listen music without my smartphone. It's really cool to use it while running or doing other sports where You leave Your phone at home. But the default music player has a problem. There is no logic how the music is sorted. You can rename them and put them in different folders, the watch will simply ignore that and list the songs randomly. So I decided to create my own music player.

## Functions
* Music will be sorted by name
* Choose a folder to play only music within it
* Play all mp3 files on the watch sorted by name
* Choose a playmode: default, repeat all, repeat one, random
* Use the headset buttons to control music
* set volume (0 - 30)

### Apple AirPods Support
Because I use Apples AirPods I added a function to have 4 Buttons instead of 2. So if You hit the AirPod twice the default function will be executed. But if You hit them 4 times the volume will change. To enable this function change the constant variable AIRPODS_QUAD_VOL to "true" and set the left and right button to the functions You defined Your AirPods to.

## How to use
* Put your music in the default directory **Music**
* You can add subfolders to generate *playlists*
* To sort the songs rename them like a playlist (i.e 01_song1.mp3, 02_song2.mp3)
* After 5 minutes not listening to music the headset buttons will not work anymore. You have to open the app and touch a button in the app to reactivate the headset buttons. This is no error, it saves battery life. (This may not work in the widget version)

## Known issues
* If you open the default music player the headset buttons will control the default player
  * Solution: Disconnect the headphones - open the new music player - and reconnect. After this steps don't open the default player again and the buttons will work forever.
* Directories within a subfolder will not be listed - the songs will be add to the parent folder playlist

Original code GitHub page: https://github.com/3ddN/Amazfit-Music-Player

![amazfit-music-player](https://user-images.githubusercontent.com/19289439/46221779-aa7d3d80-c34e-11e8-9b84-f78be819936a.jpg)
