# OpenCV Starter Kit

_A simple script to install/build OpenCV from source on macOS, and example code in C++/Python to perform image recognition_

## Requirements

* macOS (Note: the example programs should work on any OS!)
* Command line C/C++ compiler(s), e.g. GCC (if you have XCode installed, you're already good to go)
* A webcam (if you're using the example programs with a webcam stream)
* Admin rights on your Mac (to build/install OpenCV)

## Introduction

Installing [OpenCV](https://opencv.org/) (and the extra modules from [`opencv_contrib`](https://github.com/opencv/opencv_contrib)) through package managers is usually pretty straightforward, but sometimes you want to build OpenCV from source. This can be a little bit tricky on macOS; although Google can lead you to instructions on the web, they are often outdated or simply don't work.

Therefore: I wrote this little script, along with a couple of example programs (in C++ and Python) to test your OpenCV build worked.

The script will attempt to download and build the core OpenCV libraries (i.e., no example programs, tests, etc) along with setting up some virtual Python environments to make OpenCV development more pleasant if youre' a Pythonista. In addition to OpenCV, the [Homebrew](https://brew.sh/) package manager will be installed to handle some important dependencies.

## OpenCV build instructions

Simply copy the `build_opencv_macos.sh` script to wherever you'd like the OpenCV source code directories to be. I typically put them in my Desktop directory.

Next, take a quick look at the script; you should see a set of options at the start of the file (`do_`...) and an `OpenCV_version` variable to specify which version of OpenCV the script will try to install (default: `4.1.1`). The script is then divided into several sections, with each section dealing with a different aspect of the process. Hopefully the entire process is fairly clear!

If everything looks acceptable, run the script from the directory in which you would like the OpenCV source code to be placed:

	./build_opencv_macos.sh

... and prepare to enter some basic confirmations and admin passowrds when prompted.

After the script finishes, you should have a directory containing the source code of the core OpenCV libraries and extra modules, with the build results inside a directory called `build` in the main OpenCV source directory. My final directory structure looks like this:

	OpenCV-4.1.1/
		- opencv-4.1.1/
			- build/
			- ... etc ...
		- opencv-4.1.1.zip
		- opencv_contrib-4.1.1/
			- ... etc ...
		- opencv_contrib-4.1.1.zip

## Notes

* I tested the build script with clean user accounts on macOS Mojave (10.14.6) and it worked fine.