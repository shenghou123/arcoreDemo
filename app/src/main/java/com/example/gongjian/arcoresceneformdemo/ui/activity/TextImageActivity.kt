package com.example.gongjian.arcoresceneformdemo.ui.activity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.gongjian.arcoresceneformdemo.R
import com.example.gongjian.arcoresceneformdemo.arSubassembly.DoubleTapTransformableNode
import com.example.gongjian.arcoresceneformdemo.utils.DemoUtils
import com.example.gongjian.arcoresceneformdemo.utils.ToastUtils
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import kotlinx.android.synthetic.main.activity_text_image.*
import permissions.dispatcher.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@RuntimePermissions
class TextImageActivity : AppCompatActivity() {
    val TAG = "TextImageActivity";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_image)

        initViewRenderable()
        initView()
        setUpGesture()
    }


    private var hasFinishedLoading: Boolean = false

    private lateinit var textRenderable: ViewRenderable
    private lateinit var imageRenderable: ViewRenderable

    private var addObjID: Int = 0
    private val IMAGE = 1

    private var existText = false
    private var existImage = false

    private fun initViewRenderable() {
        val textFuture = ViewRenderable.builder()
                .setView(this@TextImageActivity, R.layout.renderable_text)
                .build()

        val imageFuture = ViewRenderable.builder()
                .setView(this@TextImageActivity, R.layout.renderable_image)
                .build()

        CompletableFuture.allOf(textFuture, imageFuture)
                .handle<Any> { notUsed, throwable ->
                    if (throwable != null) {
                        DemoUtils.displayError(this, "无法加载渲染模型", throwable)
                    } else {
                        try {
                            textRenderable = textFuture.get()
                            imageRenderable = imageFuture.get()
                            hasFinishedLoading = true
                        } catch (ex: InterruptedException) {
                            DemoUtils.displayError(this, "无法加载渲染模型", ex)
                        } catch (ex: ExecutionException) {
                            DemoUtils.displayError(this, "无法加载渲染模型", ex)
                        }
                    }
                    return@handle null
                }
    }

    lateinit var materialDialog: MaterialDialog.Builder

    private fun initView() {
        materialDialog = MaterialDialog.Builder(this)
                .title(R.string.input)
                .inputRangeRes(2, 20, R.color.colorPrimaryDark)
                .input(null, null, { dialog, input ->
                    //这里生成文字
                    textRenderable.view.findViewById<TextView>(R.id.UI_msg).text = input
                    addObjID = 1
                })
    }

    private fun setUpGesture() {
        //添加文字btn
        UI_addText.setOnClickListener {
            materialDialog.show()
        }

        //图片btn
        UI_addImage.setOnClickListener {
            showPhotoWithPermissionCheck()
//            showPhoto()
        }

        (UI_ArSceneView as ArFragment).setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            if (!hasFinishedLoading) {
                ToastUtils.getInstanc(this@TextImageActivity).showToast("稍等 初始化未完成")
                return@setOnTapArPlaneListener
            }

            if (addObjID == 0) {
                UI_hint.text = "选择一个需要放置的控件"
                return@setOnTapArPlaneListener
            }

            if ((addObjID == 1 && existText) || (addObjID == 2 && existImage)) {
                UI_hint.text = "当前控件已存在"
                return@setOnTapArPlaneListener
            }

            //创建一个锚点
            val anchorNode = AnchorNode(hitResult.createAnchor())
            anchorNode.setParent((UI_ArSceneView as ArFragment).arSceneView.scene)

            //创建一个可变换得到节点 在渲染对象放置在节点上
            val transformableNode = DoubleTapTransformableNode((UI_ArSceneView as ArFragment).transformationSystem)
            transformableNode.setParent(anchorNode)
            transformableNode.renderable = when (addObjID) {
                1 -> {
                    existText = true
                    textRenderable
                }
                2 -> {
                    existImage = true
                    imageRenderable
                }
                else -> {
                    null
                }
            }

            transformableNode.setOnDoubleTapListener {
                anchorNode.removeChild(transformableNode)
                when (addObjID) {
                    1 -> {
                        existText = false
                    }
                    2 -> {
                        existImage = false
                    }
                }
                addObjID = 0

            }
            addObjID = 0
            UI_hint.text = "放置成功"
            transformableNode.select()
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun showPhoto() {
        val intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE)
    }

    @OnShowRationale(Manifest.permission.CAMERA)
    fun showRationaleForCamera(request: PermissionRequest) {
        AlertDialog.Builder(this)
                .setMessage("需要这个权限获取照片")
                .setPositiveButton("同意", { dialog, button -> request.proceed() })
                .setNegativeButton("拒接", { dialog, button -> request.cancel() })
                .show()
    }

    @OnPermissionDenied(Manifest.permission.CAMERA)
    fun showDeniedForCamera() {
        Toast.makeText(this, "给个权限啦", Toast.LENGTH_SHORT).show()
    }

    @OnNeverAskAgain(Manifest.permission.CAMERA)
    fun showNeverAskForCamera() {
        Toast.makeText(this, "给个权限啦", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {

        super.onActivityResult(requestCode, resultCode, data)
        //在相册里面选择好相片之后调回到现在的这个activity中
        when (requestCode) {
            IMAGE//这里的requestCode是我自己设置的，就是确定返回到那个Activity的标志
            -> if (resultCode == Activity.RESULT_OK) {//resultcode是setResult里面设置的code值
                try {
                    val selectedImage = data.data //获取系统返回的照片的Uri
                    val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                    val cursor = contentResolver.query(selectedImage!!,
                            filePathColumn, null, null, null)//从系统表中查询指定Uri对应的照片
                    cursor!!.moveToFirst()
                    val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                    val path = cursor.getString(columnIndex)  //获取照片路径
                    cursor.close()
                    val bitmap = BitmapFactory.decodeFile(path)

                    loadImage(path)
                } catch (e: Exception) {
                    // TODO Auto-generatedcatch block
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadImage(btmap: String?) {
        Glide.with(this@TextImageActivity)
                .load(btmap)
                .apply(myOptions)
                .into(imageRenderable.view.findViewById(R.id.UI_image))
        addObjID = 2
    }

    val myOptions = RequestOptions().error(R.drawable.error_image).placeholder(R.drawable.error_image)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode,grantResults)
    }
}