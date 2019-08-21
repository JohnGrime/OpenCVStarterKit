# Simple example of using OpenCV descriptor-based matching to find a target image
# in another image or webcam feed. Supports SIFT, SURF, or ORB algorithms; the
# former two require OpenCV to be built with the optional contributions modules, as
# they are patent encumbered. ORB is standard with OpenCV, and is free for all use.
#
# Author: John Grime, The University of Oklahoma.

import sys, time
import numpy as np
import cv2 as cv

#
# Two little wrapper classes to keep things neat
#

class KeypointsAndDescriptors:

    def __init__(self):
        self.keypoints = []
        self.descriptors = []

    def DetectAndCompute(self, img, detector):
        self.keypoints, self.descriptors = detector.detectAndCompute(img, None)


class KNNMatcher:

    def __init__(self):
        self.all_matches = []
        self.good_matches = []

    def Match(self, kpd1, kpd2, matcher, Lowe_ratio_thresh = 0.7):
        self.all_matches = matcher.knnMatch(kpd1.descriptors, kpd2.descriptors, k=2)
        self.good_matches = []
        for m in self.all_matches:
            if (len(m)>=2) and (m[0].distance < Lowe_ratio_thresh*m[1].distance):
                self.good_matches.append(m[0])

#
# Simple statistics class, based on algorithms of B. P. Welford
# (via Knuth, "The Art of Computer Programming"). This algorithm
# provides variance from a running input with very little storage
# and is also robust to catastrophic cancellation.
#

class Stats:

    def __init__(self):
        self.Clear()

    def Clear(self):
        self.N = 0
        self.S = 0.0
        self.min, self.mean, self.max = 0.0, 0.0, 0.0

    def Sum(self):
        return self.mean * self.N

    def Variance(self):
        return (self.S/(self.N-1)) if (self.N>1) else 0.0

    def StdDev(self):
        return math.sqrt( self.Variance() )

    def StdErr(self):
        return math.sqrt(self.Variance()/self.N) if (self.N>1) else 0.0

    def AddSample(self, x):
        self.N += 1

        if self.N == 1:
            self.min = self.mean = self.max = x
            self.S = 0.0
            return

        delta = x-self.mean
        self.mean += delta/self.N
        self.S += delta * (x-self.mean)

        self.min = min(x,self.min)
        self.max = max(x,self.max)

#
# Statistics sets referenced by string key or numerical index
#

class StatsSet:

    def __init__(self):
        self.key_to_idx = {}
        self.stats_vec = []

    def AddName(self,key):
        if key in self.key_to_idx:
            return self.key_to_idx[key]

        s = Stats()
        idx = len(self.stats_vec)
        self.key_to_idx[key] = idx
        self.stats_vec.append(s)

        return idx

    def AddNamedSample(self,key,val):
        return self.key_to_idx[key] if (key in self.key_to_idx) else self.AddName(key)

    def AddSample(self,idx,val):
        self.stats_vec[idx].AddSample(val)
        return idx

    def Clear(self):
        for i in range(0,len(self.stats_vec)):
            self.stats_vec[i].Clear()



def printUsage(progname):
    print()
    print("Usage : %s find=path [in=path] [using=x] [superpose=x] [min=N] [every=N]" % (progname) )
    print()
    print("Where:")
    print()
    print("  find  : path to image to detect")
    print("  in    : OPTIONAL path to image in which to search (default: 'webcam', i.e. use webcam feed)")
    print("  using : OPTIONAL algorithm to use, one of 'SURF', 'SIFT', or 'ORB' (default: SIFT)")
    print("  superpose : OPTIONAL path to image to superpose onto matched region")
    print("  min   : OPTIONAL minimum N matching features before bounding box drawn (default: 4)")
    print("  every : OPTIONAL run processing every N frames (default: 1)")
    print()
    print("Notes:")
    print()
    print("The SURF and ORB algorithms can be accompanied with algorithm-specific data;")
    print("  - for SURF, this is the Hessian tolerance e.g. 'using=SURF:400' (default value: 400')")
    print("  - for ORB, this is the number of features e.g. 'using=ORB:500' (default value: 500')")
    print()
    print("The 'in' parameter can be decorated with a scale value for the data, e.g.: in=webcam:0.5,")
    print("in=mypic.png:1.5. The default scale value is 1.0 (i.e., no scaling will be performed).")
    print()

    sys.exit(-1);

def isValid(img):
	return not ((img is None) or (img.all() is None))

def loadImage(path, grayscale=True):
    img = cv.imread(path, 1)
    if isValid(img) == False:
        print( 'Unable to open file "%s"' % (path) )
        sys.exit( -1 )
    return cv.cvtColor(img,cv.COLOR_BGR2GRAY) if (grayscale) else img

#
# Off we go ...
#

useGrayscale = False
FLANN_INDEX_KDTREE, FLANN_INDEX_LSH = 1, 6
kpd, knn = KeypointsAndDescriptors(), KNNMatcher()

# Put default parameter values into map
params = {
    "find":      [""],
    "in":        ["webcam"],
    "using":     ["SIFT"],
    "superpose": [""],
    "min":       ["4"],
    "every":     ["1"],
    }

#
# Parse command line arguments
#

if len(sys.argv) < 2:
    printUsage(sys.argv[0])

for s in sys.argv[1:]:
    toks = s.split("=")
    if len(toks)>=2: params[toks[0]] = toks[1].split(":")

print("Parameters:")
for key in params:
    print("  %s : %s" % (key, ' '.join(params[key])))

minMatchesForBoundingBox = int(params["min"][0])
processEvery = int(params["every"][0])
useWebcam = (params["in"][0].lower() == "webcam")
resize = 1.0 if len(params["in"])<2 else float(params["in"][1])

#
# Load reference image and superpose image. If latter defined, also resize
# to match the reference image dimensions.
#

img_ref = loadImage(params["find"][0], useGrayscale)

img_super = None
if params["superpose"][0] != "":
    img_super = loadImage(params["superpose"][0], useGrayscale)
    rows,cols = img_ref.shape[0:2]
    img_super = cv.resize( img_super, (cols,rows), interpolation=cv.INTER_AREA )

#
# Create detector and apporpriate matcher; SIFT, SURF, or ORB.
#

algo_info = params["using"]
algo_name = algo_info[0].lower()

if (algo_name == "sift"):
    detector = cv.xfeatures2d.SIFT_create()
    matcher = cv.FlannBasedMatcher({"algorithm":FLANN_INDEX_KDTREE})
elif (algo_name == "surf"):
    minHessian = 400 if (len(algo_info)<2) else int(algo_info[1])
    detector = cv.xfeatures2d.SURF_create(minHessian)
    matcher = cv.FlannBasedMatcher({"algorithm":FLANN_INDEX_KDTREE})
elif (algo_name == "orb"):
    # Default nFReatures is 500, but that tends not to work well.
    nFeatures = 500 if (len(algo_info)<2) else int(algo_info[1])
    detector = cv.ORB_create(nFeatures)
    matcher = cv.BFMatcher(cv.NORM_HAMMING)
    # Alternative to brute force matcher:
    #matcher = cv.FlannBasedMatcher({"algorithm":FLANN_INDEX_LSH})
else:
    print("Unknown recogniser '%s'" % (algo_name))
    sys.exit(-1)

#
# Get reference keypoints/descriptors
#

kpd_ref = KeypointsAndDescriptors()
kpd_ref.DetectAndCompute(img_ref, detector)
if len(kpd_ref.keypoints) < 4:
    print("Need at least 4 keypoints (3 non-colinear) from reference image; got %d" % (len(ref_kpd.keypoints)))
    sys.exit(-1)

#
# Create output window
#

cv.namedWindow("Good Matches",1)

#
# Start webcam feed, if specified
#

if useWebcam:
    video_capture = cv.VideoCapture(0)
    if video_capture.isOpened() == False: sys.exit(-1);

#
# Process data, either from input image or looping over webcam frames
#

stats = StatsSet()
detect_idx = stats.AddName("detect")
knn_idx = stats.AddName("knn")
homography_idx = stats.AddName("homography")
draw_idx = stats.AddName("draw")
resize_idx = stats.AddName("resize")

frameNo, fpsCounter, startTime = 0, 0, time.time()

while True:

    haveTransform = False
    frameNo += 1
    fpsCounter += 1

    if useWebcam:
        ret, img = video_capture.read()
        if useGrayscale == True: img = cv.cvtColor(img, cv.COLOR_BGR2GRAY)
    else:
        img = loadImage( params["in"][0], useGrayscale )

    if resize != 1.0:
        t1 = time.time()
        img = cv.resize( img, None, resize, resize )
        stats.AddSample(resize_idx, time.time() - t1)

    if (useWebcam == False) or (frameNo%processEvery == 0):
        t1 = time.time()
        kpd.DetectAndCompute( img, detector )
        stats.AddSample(detect_idx, time.time() - t1)

        #
        # If e.g. camera is covered, we may not have any keypoint at all!
        # Need at least 4 (with 3 non-colinear) for proper homology transform
        # 

        if len(kpd.keypoints) > 4:

            t1 = time.time()
            knn.Match( kpd_ref, kpd, matcher )
            stats.AddSample(knn_idx, time.time() - t1)

            sufficientGoodMatches = (len(knn.good_matches)>minMatchesForBoundingBox)

            if (sufficientGoodMatches == True):
                t1 = time.time()
                srcPoints = np.float32([ kpd_ref.keypoints[m.queryIdx].pt for m in knn.good_matches ]).reshape(-1,1,2)
                dstPoints = np.float32([ kpd.keypoints[m.trainIdx].pt for m in knn.good_matches ]).reshape(-1,1,2)
                transform, mask = cv.findHomography( srcPoints, dstPoints, cv.RANSAC )
                haveTransform = (type(transform) == np.ndarray)
                stats.AddSample(homography_idx, time.time() - t1)

    #
    # Output to screen
    #

    rows1, cols1 = img_ref.shape[0:2]
    rows2, cols2 = img.shape[0:2]

    t1 = time.time()
    if haveTransform == True:

        #
        # Superposition image onto matched region
        #

        if isValid(img_super):
            img_tmp = cv.warpPerspective( img_super, transform, (cols2,rows2) )
            img = cv.add( img, img_tmp )

        #
        # Bounding box around matched region
        #

        srcPoints = np.float32( [[0,0],[0,rows1-1],[cols1-1,rows1-1],[cols1-1,0]] ).reshape(-1,1,2)
        dstPoints = cv.perspectiveTransform( srcPoints, transform )
        img = cv.polylines(img,[np.int32(dstPoints)],True,255,3, cv.LINE_AA)

        #
        # Draw mapping of keypoints from reference image onto matches in current image
        #

        draw_params = {"matchesMask":[], "singlePointColor":None, "matchColor":None, "flags":2}
        img_tmp = cv.drawMatches(
            img_ref, kpd_ref.keypoints,
            img, kpd.keypoints,
            knn.good_matches,
            None, **draw_params )
    else:

        #
        # Mimic image layout of cv.drawMatches(), but without any matches.
        # If we're not using grayscale, include the number of color channels
        # when we create img_tmp.
        #

        dims = max(rows1,rows2), cols1+cols2
        if useGrayscale == False:
            dims = dims[0],dims[1],img.shape[2]

        img_tmp = np.zeros(dims, dtype=img.dtype)
        img_tmp[0:rows1,     0:cols1      ] = img_ref[0:rows1, 0:cols1]
        img_tmp[0:rows2, cols1:cols1+cols2] =     img[0:rows2, 0:cols2]
    cv.imshow("Good Matches", img_tmp)
    stats.AddSample(draw_idx, time.time() - t1)

    #
    # Print some stats if needed. "potential fps" is how fast the code could run if only the image
    # processing & display time is taken into account (i.e., ignores IO bottlenecks like reading
    # from the camera etc)
    #

    now = time.time()
    deltaTime = now - startTime
    if (deltaTime>1.0):
        out = "%5.1f fps : " % (float(fpsCounter)/deltaTime)
        for key in stats.key_to_idx:
            idx = stats.key_to_idx[key]
            val = stats.stats_vec[idx].mean
            out += "%s %.2g ms : " % (key,val/1e-3)
        tmp = sum( [stats.stats_vec[idx].mean for idx in range(0,len(stats.key_to_idx))] )
        out += "%d good matches in %dx%d frame (potential %g fps)" % (
            len(knn.good_matches), img.shape[1], img.shape[0], 1.0/tmp )
        print(out)

        if haveTransform == True:
            print( "| %+8.2f %+8.2f %+8.2f |" % (transform[0][0],transform[0][1],transform[0][2]) )
            print( "| %+8.2f %+8.2f %+8.2f |" % (transform[1][0],transform[1][1],transform[1][2]) )
            print( "| %+8.2f %+8.2f %+8.2f |" % (transform[2][0],transform[2][1],transform[2][2]) )
        
        fpsCounter, startTime = 0, now
        stats.Clear()

    if useWebcam == True:
        if cv.waitKey(30) >= 0: break
    else:
        cv.waitKey()
        break
