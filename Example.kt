/*
Simple example of using OpenCV descriptor-based matching to find a target image
in another image or webcam feed. Supports SIFT, SURF, or ORB algorithms; the
former two require OpenCV to be built with the optional contributions modules, as
they are patent encumbered. ORB is standard with OpenCV, and is free for all use.

Author: John Grime, The University of Oklahoma.

Example compilation & run:

kotlinc -cp ${HOME}/Desktop/OpenCV-4.1.1/opencv-4.1.1/build/bin/opencv-411.jar Example.kt
kotlin -cp ${HOME}/Desktop/OpenCV-4.1.1/opencv-4.1.1/build/bin/opencv-411.jar:. -Djava.library.path=${HOME}/Desktop/OpenCV-4.1.1/opencv-4.1.1/build/lib/ Example find=
*/

import kotlin.system.*
import kotlin.math.*

import org.opencv.core.*
import org.opencv.core.Core.*
import org.opencv.core.CvType.*
import org.opencv.videoio.VideoCapture
import org.opencv.highgui.HighGui.*
import org.opencv.imgcodecs.Imgcodecs.*
import org.opencv.imgproc.Imgproc.*
import org.opencv.features2d.*
import org.opencv.xfeatures2d.*
import org.opencv.calib3d.*

object Example {
	init {
		System.loadLibrary(NATIVE_LIBRARY_NAME)
	}

	class KeypointsAndDescriptors {
		val descriptors = Mat()
		val keypoints = MatOfKeyPoint()

		fun DetectAndCompute(img: Mat, detector: Feature2D) {
			detector.detectAndCompute(img, Mat(), keypoints, descriptors)
		}
	}

	class KNNMatcher {
		val all_matches = arrayListOf<MatOfDMatch>()
		val good_matches = arrayListOf<DMatch>()

		fun Match(
			kpd1: KeypointsAndDescriptors,
			kpd2: KeypointsAndDescriptors,
			matcher: DescriptorMatcher,
			Lowe_ratio_thresh: Float = 0.7f ) {
				matcher.knnMatch(kpd1.descriptors, kpd2.descriptors, all_matches, 2)
				good_matches.clear()
				for (mdm in all_matches) {
				val m = mdm.toArray()
				if (m[0].distance < Lowe_ratio_thresh * m[1].distance) good_matches.add(m[0])
			}
		}
	}

	class Stats {
		var N = 0
		var S = 0.0
		var min = 0.0
		var mean = 0.0
		var max = 0.0

		fun Clear(): Unit {
			N = 0
			S = 0.0
			min = 0.0
			mean = 0.0
			max = 0.0
		}

		fun Sum(): Double {
			return mean * N
		}

		fun Variance(): Double {
			return if (N>1) (S/(N-1)) else 0.0
		}

		fun StdDev(): Double {
			return sqrt(Variance())
		}

		fun StdErr(): Double {
			return if (N>1) (Variance()/N) else (0.0)
		}

		fun AddSample(x: Double) {
			N += 1

			if (N==1) {
				min = x
				mean = x
				max = x
				return
			}

			val delta = x - mean
			mean += delta/N
			S += delta * (x-mean)

			min = min(x,min)
			max = max(x,max)
		}
	}

	fun printUsage() {
		println()
		println("Usage : Example.kt find=path [in=path] [using=x] [superpose=x] [min=N] [every=N]")
		println()
		println("Where:")
		println()
		println("  find  : path to image to detect")
		println("  in    : OPTIONAL path to image in which to search (default: 'webcam', i.e. use webcam feed)")
		println("  using : OPTIONAL algorithm to use, one of 'SURF', 'SIFT', or 'ORB' (default: SIFT)")
		println("  superpose : OPTIONAL path to image to superpose onto matched region")
		println("  min   : OPTIONAL minimum N matching features before bounding box drawn (default: 4)")
		println("  every : OPTIONAL run processing every N frames (default: 1)")
		println()
		println("Notes:")
		println()
		println("The SURF and ORB algorithms can be accompanied with algorithm-specific data;")
		println("  - for SURF, this is the Hessian tolerance e.g. 'using=SURF:400' (default value: 400')")
		println("  - for ORB, this is the number of features e.g. 'using=ORB:500' (default value: 500')")
		println()
		println("The 'in' parameter can be decorated with a scale value for the data, e.g.: in=webcam:0.5,")
		println("in=mypic.png:1.5. The default scale value is 1.0 (i.e., no scaling will be performed).")
		println()

		exitProcess(-1)
	}

	fun loadImage(path: String, grayscale: Boolean = true ): Mat {
		val img = imread(path)
		if (img.empty()) {
			println("Could not load image ${path}")
			exitProcess(-1)
		}
		if (grayscale) cvtColor( img, img, COLOR_BGR2GRAY )
		return img
	}

	@JvmStatic fun main(args: Array<String>) {
		val useGrayscale = true

		var useWebcam = true
		var resize = 1.0

		val drawMatchesMask = MatOfByte()
		var img = Mat()
		var img_tmp = Mat()
		var img_super = Mat()
		var transform = Mat()

		val kpd = KeypointsAndDescriptors()
		val kpd_ref = KeypointsAndDescriptors()
		val knn = KNNMatcher()

		val srcPoints = arrayListOf<Point>()
		val dstPoints = arrayListOf<Point>()

		val webcam = VideoCapture()

		println("OpenCV version " + VERSION)
		if (args.size < 1) printUsage()

		val params = mutableMapOf(
			"find"      to listOf(""),
			"in"        to listOf("webcam"),
			"using"     to listOf("SIFT"),
			"superpose" to listOf(""),
			"min"       to listOf("4"),
			"every"     to listOf("1")
			)

		for (p in args) {
			val toks = p.split("=")
			if (toks.size<2) continue
			params.put(toks[0], toks[1].split(":"))
		}

		println( "Parameters:" )
		for (p in params) {
			println("  ${p.key} : ${p.value}")
		}

		var info = params.get("using")!!

		val detector = when (info[0].toLowerCase()) {
			"sift" -> SIFT.create()
			"surf" -> SURF.create(if(info.size>1) info[1].toDouble() else 400.0)
			"orb" -> ORB.create(if(info.size>1) info[1].toInt() else 500)
			else -> null
		}

		val matcher = when (info[0].toLowerCase()) {
			"sift" -> FlannBasedMatcher.create()
			"surf" -> FlannBasedMatcher.create()
			"orb" -> BFMatcher.create(NORM_HAMMING)
			else -> null
		}

		if (detector == null || matcher == null) {
			println("Unknown algorithm type ${info[0]}")
			exitProcess(-1)
		}

		info = params.get("find")!!
		val img_ref = loadImage(info[0], useGrayscale)

		info = params.get("min")!!
		val minMatchesForBoundingBox = info[0].toInt()

		info = params.get("every")!!
		val processEvery = info[0].toInt()

		info = params.get("in")!!
		if (info[0] != "webcam") {
			val test = "${info[0]}"
			println( "!!!!! => ${test}" )
			img = loadImage(test, useGrayscale)
			useWebcam = false
		}
		if (info.size>1) resize = info[1].toDouble()

		info = params.get("superpose")!!
		if (info[0] != "") {
			img_super = loadImage(info[0], useGrayscale)
			resize(img_super, img_super, img_ref.size())
		}

		kpd_ref.DetectAndCompute(img_ref, detector)

		namedWindow("Good Matches",1)

		if (useWebcam) {
			webcam.open(0)
			if (!webcam.isOpened()) {
				println("Unable to open webcam")
				exitProcess(-1)
			}
		}

		var resize_stats = Stats()
		var detect_stats = Stats()
		var knn_stats = Stats()
		var homography_stats = Stats()
		var draw_stats = Stats()

		var fpsCounter = 0
		var frameNo = 0

		var start_ns = System.nanoTime()
		while(true) {
			var haveTransform = false
			fpsCounter++
			frameNo++

			if (useWebcam) {
				webcam.read(img)
				if (useGrayscale) cvtColor( img, img, COLOR_BGR2GRAY )
			}
			else img = loadImage(args[1], useGrayscale)

			if (resize != 1.0) {
				var t1 = System.nanoTime()
				resize(img, img, Size(), resize, resize)
				resize_stats.AddSample( (System.nanoTime()-t1).toDouble() )
			}

			if ((!useWebcam) || (frameNo%processEvery == 0))
			{
				var t1 = System.nanoTime()
				kpd.DetectAndCompute(img, detector)
				detect_stats.AddSample( (System.nanoTime()-t1).toDouble() )

				if (kpd.keypoints.size(0) > 4)
				{
					//
					// KNN matching
					//

					t1 = System.nanoTime()
					knn.Match(kpd_ref, kpd, matcher)
					knn_stats.AddSample( (System.nanoTime()-t1).toDouble() )

					if (knn.good_matches.size > minMatchesForBoundingBox) {
						t1 = System.nanoTime()
						//
						// Find homography and transform for image of interest.
						// Replace with something else to avoid camera calib module?
						//

						val ref_kpts = kpd_ref.keypoints.toArray()
						val kpts = kpd.keypoints.toArray()
						
						for (m in knn.good_matches)
						{
							srcPoints.add( ref_kpts[m.queryIdx].pt )
							dstPoints.add( kpts[m.trainIdx].pt )
						}
						// def. reproj. value is 3.0 per OpenCV 4.1.1; smaller = slower?

						val src = MatOfPoint2f()
						val dst = MatOfPoint2f()

						src.fromList(srcPoints)
						dst.fromList(dstPoints)

						transform = Calib3d.findHomography( src, dst, Calib3d.RANSAC )
						haveTransform = (!transform.empty())
						homography_stats.AddSample( (System.nanoTime()-t1).toDouble() )
					}
				}
			}

			val cols1 = img_ref.cols()
			val rows1 = img_ref.rows()

			val cols2 = img.cols()
			val rows2 = img.rows()

			var t1 = System.nanoTime()
			if (haveTransform) {
				//
				// Transform superposition image; consider smaller output mat, zero
				// translation components of transform matrix, then explicit translate
				// to save memory / CPU time in add()?
				//

				if (!img_super.empty()) {
					warpPerspective( img_super, img_tmp, transform, img.size())
					add( img, img_tmp, img )
				}

				val good_matches = MatOfDMatch()
				good_matches.fromList(knn.good_matches)

				Features2d.drawMatches(
					img_ref, kpd_ref.keypoints,
					img, kpd.keypoints,
					good_matches,
					img_tmp,
					Scalar.all(-1.0), Scalar.all(-1.0),
					drawMatchesMask,
					Features2d.DrawMatchesFlags_NOT_DRAW_SINGLE_POINTS )
				}
				else {
					img_tmp = Mat.zeros(max(rows1,rows2), cols1+cols2, img.type())
					img_ref.copyTo( img_tmp.submat(Rect(0,0,cols1,rows1)) )
					img.copyTo( img_tmp.submat(Rect(cols1,0,cols2,rows2)) )
				}
				imshow("Good Matches", img_tmp)
				draw_stats.AddSample( (System.nanoTime()-t1).toDouble() )

			//
			// Print some stats etc every second
			//

			val elapsed_ns = System.nanoTime() - start_ns
			if (elapsed_ns >= 1_000_000_000) {

				start_ns = System.nanoTime()

				val potential_fps = 1_000_000_000 * 1.0 / (
					resize_stats.mean +
					detect_stats.mean +
					resize_stats.mean +
					knn_stats.mean +
					homography_stats.mean + 	
					draw_stats.mean )

				var s: String
				s  = "%.1f fps : ".format(1_000_000_000 * fpsCounter.toDouble() / elapsed_ns)
				s += "resize %.2g ms : ".format(resize_stats.mean/1_000_000)
				s += "detect %.2g ms : ".format(detect_stats.mean/1_000_000)
				s += "knn %.2g ms : ".format(knn_stats.mean/1_000_000)
				s += "homography %.2g ms : ".format(homography_stats.mean/1_000_000)
				s += "draw %.2g ms : ".format(homography_stats.mean/1_000_000)
				s += "%d matches in %dx%d frame (potential %.2g fps)".format(knn.good_matches.size, cols2,rows2, potential_fps)
				println(s)

				if (haveTransform) {
					s  = "| +%6.2f +%6.2f +%6.2f |\n".format(transform.get(0,0)[0], transform.get(0,1)[0], transform.get(0,2)[0])
					s += "| +%6.2f +%6.2f +%6.2f |\n".format(transform.get(1,0)[0], transform.get(1,1)[0], transform.get(1,2)[0])
					s += "| +%6.2f +%6.2f +%6.2f |\n".format(transform.get(2,0)[0], transform.get(2,1)[0], transform.get(2,2)[0])
					println(s)
				}

				fpsCounter = 0

				resize_stats.Clear()
				detect_stats.Clear()
				knn_stats.Clear()
				homography_stats.Clear()
				draw_stats.Clear()
			}

			if (useWebcam) {
				if (waitKey(30)>=0) break
			}
			else {
				waitKey()
				break
			}
		}

		exitProcess(-1)
	}
}
