# OpenCV Starter Kit

_A simple script to install/build OpenCV from source on macOS, and example code in C++/Python to perform image recognition_

Note: although the build script is macOS-oriented, the example programs should work on any OS!

## Requirements

* macOS (tested on macOS Mojave, 10.14.6)
* Command line C/C++ compiler(s), e.g. GCC (if you have XCode installed, you're already good to go)
* Admin rights on your Mac (to build/install OpenCV)
* A webcam (if you're using the example programs with a webcam stream)

## Introduction

Installing [OpenCV](https://opencv.org/) (and the extra modules from [`opencv_contrib`](https://github.com/opencv/opencv_contrib)) through package managers is usually pretty straightforward, but sometimes you want to build it from source. This can be a bit tricky on macOS; although Google can lead you to instructions on the web, they are often outdated or simply don't work.

Therefore: I wrote this little script, along with a couple of example programs (in C++ and Python) to test your OpenCV build worked.

The script will attempt to download and build the core OpenCV libraries (i.e., no example programs, tests, etc) along with setting up some [virtual Python environments](https://virtualenvwrapper.readthedocs.io/en/latest/) with Python v3 to make OpenCV development more pleasant if youre' a Pythonista. In addition to OpenCV, the [Homebrew](https://brew.sh/) package manager will be installed to handle some important dependencies.

## Important note about security

Apple tightened up security in macOS (sometime around High Sierra?) and placed additional protections on some files and directories in e.g. `/usr/local/`. This can prevent Homebrew successfully running some post-install scripts; in particular, you can find that [pip](https://pip.pypa.io/en/stable/) is not available after installing Python v3 through homebrew, despite being part of the Python3 distribution. As `pip3` is used by our build script, it explicitly grants ownership of `/usr/local/` to the current user to enable `pip3` to be available after Python v3 is installed.

One alternative approach is to install the `pip` functionality through python itself; this is available in the build script by changing the `pip_install_option` variable. See the build script itself for more details.

The tightened security also manifests as macOS prompting the user for permission before allowing programs to access the system webcam. If the example C++ program cannot open the webcam at runtime, one potential reason is that macOS has not granted webcam permissions to command-line programs; unfortunately, simply running the C++ example program doesn't seem to make macOS prompt the user to allow webcam access! If you're having problems of this nature, try running the example Python script - for some reason, the Python script seems to trigger an access prompt even if the C++ example program doesn't. After you accept that access request, the C++ example (and indeed, all other command-line programs) should be able to access the webcam. You can disable this access through `System Preferences -> Security & Privacy -> Privacy -> Camera`.

## OpenCV build instructions

Simply copy the `build_opencv_macos.sh` script to wherever you'd like the OpenCV source code directories to be. I typically put them in my Desktop directory.

Next, take a quick look at the script; you should see a set of options at the start of the file (`do_`...) and an `OpenCV_version` variable to specify which version of OpenCV the script will try to install (default: `4.1.1`). The script is then divided into several sections, with each section dealing with a different aspect of the process.

If everything looks acceptable, run the script from the directory in which you would like the OpenCV source code to be placed:

	./build_opencv_macos.sh

... and prepare to enter some basic confirmations and admin passowrds when prompted. The whole process (get support tools, download OpenCV source code then configure, build, and install) should take around 15 minutes.

After the script finishes, you should have a directory containing the source code of the core OpenCV libraries and extra modules, with the build results inside a directory called `build` in the main OpenCV source directory. My final directory structure looks like this:

	OpenCV-4.1.1/
	|-- opencv-4.1.1.zip
	|-- opencv-4.1.1/
	|   |-- build/
	|   |-- ... etc ...
	|-- opencv_contrib-4.1.1.zip
	|-- opencv_contrib-4.1.1/
	|   |-- ... etc ...

## Example programs

Included in this repository are two example programs (with essentially identical functionality) written in C++ and Python. The programs:

1. Read an image file of interest, and then ...
2. Attempt to identify that image in either another image file or in a live video stream from the user's webcam.

_Please see the note about webcam access for command-line programs in the section `Important note about security`!_
