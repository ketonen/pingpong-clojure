# pingpong-clojure

## Practice project for my clojure journey

<img src="Screenshot.png" alt="drawing" height="300px"/>

You can play local or online game. In practise, if application is deployed to hosting service two players can play agains each other with different computers (requires some minor tweaks currently).

Application contains two projects. Server and client. All game logic is on the server side and game state is transferred to client throw socket.

When game is running, only right and left movement is transferred to server side.
