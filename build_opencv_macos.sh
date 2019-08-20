#!/usr/bin/env bash

#
# This script assumes the user has admin rights. El Capitan (macOS 10.11) tightened up scurity:
#
# 1. Homebrew seems to have problems modifying with the /usr/local directory. This is addressed
# in this script by explicitly setting access rights (although this behaviour can be changed).
#
# 2. The webcam can't be accessed from the terminal using OpenCV in Python or C++ until you
# accept a system prompt to grant access. Simply running a C++ executable does not seem to
# generate a prompt; running a Python script that accesses the camera via OpenCV *does* generate
# a prompt, after which you can also run the C++ executable without issues.
#

# Options to control what the script does; comment options out if/as appropriate
do_homebrew_install="yes"
do_pip_install="yes"
do_misc_install="yes"
do_source_install="yes"
do_build="yes"
do_install="yes"
do_symlink="yes"

# Choose your desired OpenCV version, and a name for the Python virtual environments directory
OpenCV_version="4.1.1"
VirtualEnvs_dir=".virtualenvs"

# To which profile script should we add any required path setup etc?
profile_file="${HOME}/.bash_profile"

# Some default directory names
OpenCV_dir="${workdir}/OpenCV-${OpenCV_version}"
OpenCV_source_dir="${OpenCV_dir}/opencv-${OpenCV_version}"
OpenCV_contrib_dir="${OpenCV_dir}/opencv_contrib-${OpenCV_version}"

# The directory from which this script was run
workdir=$(pwd)

# ---------------------------------------------------------------
# 1. Install Homebrew ( https://brew.sh/ ) and ensure up-to-date:
# ---------------------------------------------------------------

if [[ "${do_homebrew_install}" == "yes" ]]
then

	echo ""
	echo "#"
	echo "# Installing Homebrew ..."
	echo "#"
	echo ""

	# Homebrew website suggests this as a removal command ( https://docs.brew.sh/FAQ ):
	# ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/uninstall)"

	if [[ $(which brew) == "" ]]
	then
		/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
	fi

	brew update
fi


# --------------------------------
# 2. Install Python3 via Homebrew:
# --------------------------------

#
# Homebrew on macOS El Capitan (10.11) and above has problems with /usr/local/
# which breaks some post-install scripts for python; this manifests as e.g.
# no "pip3" command (though "python3 -m pip ..." works).
#
# We therefore have multiple approaches:
#
# 1. Ensure certain required directories are created, and set access rights
#    that allow Homebrew to manipulate them, before installing python
#
# 2. Install pip explicitly after python installed
#
# In any case, "brew install python" should result in the availability of
# both python2 (>= 2.7.1) and python3 (>= 3.7.4). TensorFlow etc are now
# compatible with python v3.7, so don't worry about needing v3.6 etc.
#
# Here, I'm using approach #1.
#

pip_install_option="1"

if [[ "${do_pip_install}" == "yes" ]]
then

	echo ""
	echo "#"
	echo "# Installing pip3 ..."
	echo "#"
	echo ""

	if [[ $(which brew) == "" ]]
	then
		echo "PROBLEM: No brew command found - did the Homebrew install work?"
		exit
	fi

	if [[ "${pip_install_option}" == "1" ]]
	then

		sudo mkdir -p /usr/local/lib
		sudo mkdir -p /usr/local/Frameworks
		sudo chown -R $(whoami) $(brew --prefix)/*
		brew install python

	else
		brew install python
		curl -O https://bootstrap.pypa.io/get-pip.py
		sudo python3 get-pip.py
		sudo rm -rf get-pip.py ~/.cache/pip
	fi

fi


# ------------------------------------------------------------
# 3. Install misc prerequisites for building and using OpenCV:
# ------------------------------------------------------------

if [[ "${do_misc_install}" == "yes" ]]
then

	echo ""
	echo "#"
	echo "# Installing misc. items ..."
	echo "#"
	echo ""

	if [[ $(which brew) == "" ]]
	then
		echo "PROBLEM: No brew command found - did the Homebrew install work?"
		exit
	fi

	if [[ $(which pip3) == "" ]]
	then
		echo "PROBLEM: No pip3 command found - did the Python3 install work?"
		exit
	fi

	brew install cmake
	brew install jpeg libpng libtiff openexr
	brew install eigen tbb

	sudo pip3 install virtualenvwrapper

	# Add some lines to profile file so we can use virtual Python environments:

	echo ""                                                       >> ${profile_file}
	echo "export WORKON_HOME=$HOME/${VirtualEnvs_dir}"            >> ${profile_file}
	echo "export VIRTUALENVWRAPPER_PYTHON=/usr/local/bin/python3" >> ${profile_file}
	echo "source /usr/local/bin/virtualenvwrapper.sh"             >> ${profile_file}

	# Ensure new environment settings are active in the current shell:

	source ${profile_file}

	# Create a virtual python environment called "cv", ensure numpy is intalled in it.

	mkvirtualenv cv -p python3

	#
	# Ensure numpy is available in the "cv" virtual Python environment; virtual environments
	# don't inherit from the system environment by default. Note that recent macOS versions
	# ship with numpy in the python2 environment, so try not to get confused ...
	#

	workon cv
	sudo pip3 install numpy

fi


# --------------------------------
# 4. Get OpenCV and contributions:
# --------------------------------

if [[ "${do_source_install}" == "yes" ]]
then

	source ${profile_file}
	workon cv

	echo ""
	echo "#"
	echo "# Installing source code for OpenCV and contributions ..."
	echo "#"
	echo ""

	mkdir -p "${OpenCV_dir}"

	cd ${OpenCV_dir}

	curl -L -o "opencv-${OpenCV_version}.zip"         https://github.com/opencv/opencv/archive/${OpenCV_version}.zip
	curl -L -o "opencv_contrib-${OpenCV_version}.zip" https://github.com/opencv/opencv_contrib/archive/${OpenCV_version}.zip

	unzip "opencv-${OpenCV_version}.zip"
	unzip "opencv_contrib-${OpenCV_version}.zip"

	cd ${workdir}

fi


# ----------------------------------
# 5. Build OpenCV and contributions:
# ----------------------------------

if [[ "${do_build}" == "yes" ]]
then

	source ${profile_file}
	workon cv

	echo ""
	echo "#"
	echo "# Building OpenCV and contributions ..."
	echo "#"
	echo ""

	if [[ ! -d "${OpenCV_source_dir}" ]]
	then
		echo "No OpenCV source dir; expected '${OpenCV_source_dir}'"
		exit
	fi

	if [[ ! -d "${OpenCV_contrib_dir}" ]]
	then
		echo "No OpenCV contrib source dir; expected '${OpenCV_contrib_dir}'"
		exit
	fi

	cd ${OpenCV_source_dir}

	if [[ ! -d build ]]
	then
		mkdir build
	fi

	cd build

	#
	# Get version'd python library file path.
	#

	cfg=$(python-config --configdir)
	maj=$(python --version | awk '{split($2,t,"."); print t[1]}')
	min=$(python --version | awk '{split($2,t,"."); print t[2]}')
	py_lib="${cfg}/libpython${maj}.${min}.dylib"

	#
	# Get Python include directory: Skip leading "-I" of first token in results from
	# "python-config --includes", and add a trailing slash.
	#

	tmp=$(python-config --includes | awk '{print $1}')
	py_inc="${tmp:2}/"

	#
	# Prepare makefiles etc; assumes e.g. "workon" used to set Python evironment,
	# and so "which python3" should return correct information. We override the
	# default Python executable to use Python3.
	#

	cmake \
	    -D CMAKE_BUILD_TYPE=RELEASE \
	    -D CMAKE_INSTALL_PREFIX=/usr/local \
	    -D OPENCV_EXTRA_MODULES_PATH="${OpenCV_contrib_dir}/modules" \
	    -D OPENCV_ENABLE_NONFREE=ON \
	    -D PYTHON3_LIBRARY=${py_lib} \
	    -D PYTHON3_INCLUDE_DIR=${py_inc} \
	    -D PYTHON3_EXECUTABLE=$(which python3) \
	    -D PYTHON_DEFAULT_EXECUTABLE=$(which python3) \
	    -D BUILD_opencv_python2=OFF \
	    -D BUILD_opencv_python3=ON \
	    -D BUILD_PERF_TESTS=OFF \
	    -D BUILD_TESTS=OFF \
	    ..

	#
	# Parallel make - as many processors as you feel brave enough to use!
	#

	make -j 8

	cd ${workdir}

fi


# ------------------------------------
# 6. Install OpenCV and contributions:
# ------------------------------------

if [[ "${do_install}" == "yes" ]]
then

	source ${profile_file}
	workon cv

	echo ""
	echo "#"
	echo "# Installing OpenCV and contributions ..."
	echo "#"
	echo ""

	build_dir="${OpenCV_source_dir}/build"

	if [[ ! -d "${build_dir}" ]]
	then
		echo "No OpenCV build dir; expected '${build_dir}'"
		exit
	fi

	cd "${build_dir}"

	sudo make install

	cd ${workdir}

	# Ensure we can find our OpenCV on the Python path! Otherwise, we may have import problems.
	echo "export PYTHONPATH=\${PYTHONPATH}:${build_dir}/python_loader" >> ${profile_file}

fi

# -------------------------------------------
# 7. Symlink so "import cv2" works in python:
# -------------------------------------------

if [[ "${do_symlink}" == "yes" ]]
then

	source ${profile_file}
	workon cv

	echo ""
	echo "#"
	echo "# Symlinking cv2 object ..."
	echo "#"
	echo ""

	py_maj=$(python --version | awk '{split($2,t,"."); print t[1]}')
	py_min=$(python --version | awk '{split($2,t,"."); print t[2]}')

	src="/usr/local/lib/python${py_maj}.${py_min}/site-packages/cv2/python-{py_maj}.${py_min}/cv2.cpython-${py_maj}${py_min}m-darwin.so"
	dst="${HOME}/${VirtualEnvs_dir}/cv/lib/python${py_maj}.${py_min}/site-packages/cv2.so"

	echo "Adding symbolic link: ln -s ${src} ${dst}"

	ln -s ${src} ${dst}

fi
