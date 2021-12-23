# knarr

Knarr is an extension to [Phosphor](https://github.com/gmu-swe/phosphor) that uses Phosphor's taint tracking engine to track path constraints. Those path constraints can then be used for concolic execution, as our project [CONFETTI](https://github.com/neu-se/confetti) does.

## Building

Knarr requires Java 8, and builds with Maven. 

## Using 
Knarr was developed to be a runtime system component leveraged by a fuzzer. Its API design is lightly documented, but we make it available publicly as a stand-alone component in case others find it useful.
We suggest that you use Knarr by interacting with it through the `edu.gmu.swe.knarr.runtime.Symbolicator` class, which provides methods to mark values as symbolic and retrieve the attached constraints.


## Development Status

We continue to maintain this codebase, and welcome contributions. 

If you run into any problems, please feel free to reach out to us by email, or by opening an issue or pull request on GitHub.
