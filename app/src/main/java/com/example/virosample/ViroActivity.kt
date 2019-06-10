/*
 * Copyright (c) 2017-present, Viro, Inc.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.virosample

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup

import com.viro.core.ARAnchor
import com.viro.core.ARNode
import com.viro.core.ARPlaneAnchor
import com.viro.core.ARScene
import com.viro.core.AsyncObject3DListener
import com.viro.core.ClickListener
import com.viro.core.ClickState
import com.viro.core.DragListener
import com.viro.core.Material
import com.viro.core.Node
import com.viro.core.Object3D

import com.viro.core.OmniLight
import com.viro.core.Surface
import com.viro.core.Texture
import com.viro.core.Vector
import com.viro.core.ViroView
import com.viro.core.ViroViewARCore
import com.viro.core.ViroViewScene
import common.CustomSerializer
import common.Request
import java.io.File

import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.Socket
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet

/**
 * Activity that initializes Viro and ARCore. This activity builds an AR scene that continuously
 * detects planes. If you tap on a plane, an Android will appear at the location tapped. The
 * Androids are rendered using PBR (physically-based rendering).
 */
class ViroActivity : Activity() {
    private var mViroView: ViroView? = null
    private var mScene: ARScene? = null
    private var listen = false
    private var time_before = mutableListOf<Long?>()

    private lateinit var client: Socket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        mViroView = ViroViewARCore(this, object : ViroViewARCore.StartupListener {
            override fun onSuccess() {
                displayScene()
            }

            override fun onFailure(error: ViroViewARCore.StartupError, errorMessage: String) {
                Log.e(TAG, "Error initializing AR [$errorMessage]")
            }
        })
        setContentView(mViroView)
    }

    /**
     * Create an AR scene that tracks planes. Tapping on a plane places a 3D Object on the spot
     * tapped.
     */
    private fun displayScene() {
        // Create the 3D AR scene, and display the point cloud
        mScene = ARScene()
        mScene!!.displayPointCloud(true)
        mViroView!!.scene = mScene
        // Create a TrackedPlanesController to visually display identified planes.
        val controller = TrackedPlanesController(this)

        // Spawn a 3D Droid on the position where the user has clicked on a tracked plane.
        controller.addOnPlaneClickListener(object : ClickListener {
            override fun onClick(i: Int, node: Node, clickPosition: Vector) {
                createDroidAtPosition(clickPosition)
            }

            override fun onClickState(i: Int, node: Node, clickState: ClickState, vector: Vector) {
                //No-op
            }
        })
        View.inflate(this, R.layout.viro_view_ar_hud, mViroView as ViewGroup)
        mScene!!.setListener(controller)

        // Add some lights to the scene; this will give the Android's some nice illumination.
        val rootNode = mScene!!.rootNode
        val lightPositions = ArrayList<Vector>()
        lightPositions.add(Vector(-10f, 10f, 1f))
        lightPositions.add(Vector(10f, 10f, 1f))

        val intensity = 300f
        val lightColors = ArrayList<Int>()
        lightColors.add(Color.WHITE)
        lightColors.add(Color.WHITE)

        for (i in lightPositions.indices) {
            val light = OmniLight()
            light.color = lightColors.get(i).toLong()
            light.position = lightPositions[i]
            light.attenuationStartDistance = 20f
            light.attenuationEndDistance = 30f
            light.intensity = intensity
            rootNode.addLight(light)
        }

        //Add an HDR environment map to give the Android's more interesting ambient lighting.
        val environment = Texture.loadRadianceHDRTexture(Uri.parse("file:///android_asset/ibl_newport_loft.hdr"))
        mScene!!.lightingEnvironment = environment

        mViroView!!.scene = mScene
    }

    /**
     * Create an Android object and have it appear at the given location.
     * @param position The location where the Android should appear.
     */



    fun showPopup(v:View) {
        val builder = AlertDialog.Builder(this)
        val itemsList = arrayOf<CharSequence>("Client open","Listener start","Client close")
        builder.setTitle("Client option")
                .setItems(itemsList) { dialog, which ->
                    when (which) {
                        0 -> {


                            client = Socket("192.168.178.30", 9999) //connect to server
                            listen = true


                            if (ContextCompat.checkSelfPermission(this,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    != PackageManager.PERMISSION_GRANTED) {

                                // Permission is not granted
                                // Should we show an explanation?
                                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                                    // Show an explanation to the user *asynchronously* -- don't block
                                    // this thread waiting for the user's response! After the user
                                    // sees the explanation, try again to request the permission.
                                } else {
                                    // No explanation needed, we can request the permission.
                                    ActivityCompat.requestPermissions(this,
                                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                            0)

                                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                                    // app-defined int constant. The callback method gets the
                                    // result of the request.
                                }
                            } else {
                                // Permission has already been granted
                            }


                        }

                        1 -> {
                            mViroView!!.setCameraListener { position, rotation, forward ->

                                if (listen) {
                                    val timestamp = System.currentTimeMillis()
                                    time_before.add(timestamp)
                                    val request = Request(position, rotation, forward)
                                    val serializer = CustomSerializer()
                                    val requestBytes = serializer.Serialize(request)
                                    val output = client.outputStream
                                    output.write(requestBytes)



                                }


                            }
                        }
                        2 -> {

                            client.close()
                            listen = false






                            val fileName = "/sdcard/time_before.txt"
                            val myfile = File(fileName)
                            myfile.createNewFile()
                            myfile.printWriter().use { out ->

                                out.write(time_before.toString())

                            }


                        }




                    }
                }
        val d = builder.create()
        d.show()
    }


















    private fun createDroidAtPosition(position: Vector) {
        // Create a droid on the surface
        val bot = getBitmapFromAsset(this, "andy.png")
        val object3D = Object3D()
        object3D.setPosition(position)

        mScene!!.rootNode.addChildNode(object3D)

        // Load the Android model asynchronously.
        object3D.loadModel(mViroView!!.viroContext, Uri.parse("file:///android_asset/andy.obj"), Object3D.Type.OBJ, object : AsyncObject3DListener {
            override fun onObject3DLoaded(`object`: Object3D, type: Object3D.Type) {
                // When the model is loaded, set the texture associated with this OBJ
                val objectTexture = Texture(bot!!, Texture.Format.RGBA8, false, false)
                val material = Material()
                material.diffuseTexture = objectTexture

                // Give the material a more "metallic" appearance, so it reflects the environment map.
                // By setting its lighting model to PHYSICALLY_BASED, we enable PBR rendering on the
                // model.
                material.roughness = 0.23f
                material.metalness = 0.7f
                material.lightingModel = Material.LightingModel.PHYSICALLY_BASED

                object3D.geometry.materials = Arrays.asList(material)
            }

            override fun onObject3DFailed(s: String) {}
        })

        // Make the object draggable.
        object3D.dragListener = DragListener { i, node, vector, vector1 ->
            // No-op.
        }
        object3D.dragType = Node.DragType.FIXED_DISTANCE
    }

    /**
     * Tracks planes and renders a surface on them so the user can see where we've identified
     * planes.
     */
    private class TrackedPlanesController(activity: Activity) : ARScene.Listener {
        private val mCurrentActivityWeak: WeakReference<Activity>
        private var searchingForPlanesLayoutIsVisible = false
        private val surfaces = HashMap<String, Node>()
        private val mPlaneClickListeners = HashSet<ClickListener>()

        override fun onTrackingUpdated(trackingState: ARScene.TrackingState, trackingStateReason: ARScene.TrackingStateReason) {
            //no-op
        }

        init {
            mCurrentActivityWeak = WeakReference(activity)


        }

        fun addOnPlaneClickListener(listener: ClickListener) {
            mPlaneClickListeners.add(listener)
        }




        /**
         * Once a Tracked plane is found, we can hide the our "Searching for Surfaces" UI.
         */
        private fun hideIsTrackingLayoutUI() {
            if (searchingForPlanesLayoutIsVisible) {
                return
            }
            searchingForPlanesLayoutIsVisible = true




        }

        override fun onAnchorFound(arAnchor: ARAnchor, arNode: ARNode) {
            // Spawn a visual plane if a PlaneAnchor was found
            if (arAnchor.type == ARAnchor.Type.PLANE) {
                val planeAnchor = arAnchor as ARPlaneAnchor

                // Create the visual geometry representing this plane
                val dimensions = planeAnchor.extent
                val plane = Surface(1f, 1f)
                plane.width = dimensions.x
                plane.height = dimensions.z

                // Set a default material for this plane.
                val material = Material()
                material.diffuseColor = Color.parseColor("#BF000000")
                plane.materials = Arrays.asList(material)

                // Attach it to the node
                val planeNode = Node()
                planeNode.geometry = plane
                planeNode.setRotation(Vector(-Math.toRadians(90.0), 0.0, 0.0))
                planeNode.setPosition(planeAnchor.center)

                // Attach this planeNode to the anchor's arNode
                arNode.addChildNode(planeNode)
                surfaces[arAnchor.getAnchorId()] = planeNode

                // Attach click listeners to be notified upon a plane onClick.
                planeNode.clickListener = object : ClickListener {
                    override fun onClick(i: Int, node: Node, vector: Vector) {
                        for (listener in mPlaneClickListeners) {
                            listener.onClick(i, node, vector)
                        }
                    }

                    override fun onClickState(i: Int, node: Node, clickState: ClickState, vector: Vector) {
                        //No-op
                    }
                }

                // Finally, hide isTracking UI if we haven't done so already.
                hideIsTrackingLayoutUI()
            }
        }

        override fun onAnchorUpdated(arAnchor: ARAnchor, arNode: ARNode) {
            if (arAnchor.type == ARAnchor.Type.PLANE) {
                val planeAnchor = arAnchor as ARPlaneAnchor

                // Update the mesh surface geometry
                val node = surfaces[arAnchor.getAnchorId()]
                val plane = node!!.geometry as Surface
                val dimensions = planeAnchor.extent
                plane.width = dimensions.x
                plane.height = dimensions.z
            }
        }

        override fun onAnchorRemoved(arAnchor: ARAnchor, arNode: ARNode) {
            surfaces.remove(arAnchor.anchorId)
        }

        override fun onTrackingInitialized() {
            //No-op
        }

        override fun onAmbientLightUpdate(lightIntensity: Float, lightColor: Vector) {
            //No-op
        }
    }

    private fun getBitmapFromAsset(context: Context, assetName: String): Bitmap? {
        val assetManager = context.resources.assets
        val imageStream: InputStream
        try {
            imageStream = assetManager.open(assetName)
        } catch (exception: IOException) {
            Log.w(TAG, "Unable to find image [" + assetName + "] in assets! Error: "
                    + exception.message)
            return null
        }

        return BitmapFactory.decodeStream(imageStream)
    }

    override fun onStart() {
        super.onStart()
        mViroView!!.onActivityStarted(this)
    }

    override fun onResume() {
        super.onResume()
        mViroView!!.onActivityResumed(this)
    }

    override fun onPause() {
        super.onPause()
        mViroView!!.onActivityPaused(this)
    }

    override fun onStop() {
        super.onStop()
        mViroView!!.onActivityStopped(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mViroView!!.onActivityDestroyed(this)
    }

    companion object {
        private val TAG = ViroActivity::class.java!!.getSimpleName()
    }
}

