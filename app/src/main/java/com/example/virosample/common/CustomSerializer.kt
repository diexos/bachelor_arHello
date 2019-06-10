package common

import com.viro.core.Vector
import java.nio.charset.Charset

class CustomSerializer : ISerializer {

    override fun Serialize(request: Request): ByteArray {
        val text = "${request.position},${request.rotation},${request.forward}\n"
        return text.toByteArray(Charset.defaultCharset())
    }


    override fun Serialize(response: Response): ByteArray = "${response.Success},${response.Result}".toByteArray(Charset.defaultCharset())

    override fun DeserializeRequest(bytes: ByteArray): Request {
        val text = String(bytes, Charset.defaultCharset())

        val parts = text.split(",*")
        var pos_array = FloatArray(3){parts[0].toFloat()}
        var rot_array = FloatArray(3){i->parts[i+3].toFloat()}
        var for_array = FloatArray(3){i->parts[i+6].toFloat()}
        var pos_vec = Vector(pos_array)
        var rot_vec = Vector(rot_array)
        var for_vec = Vector(for_array)
        return Request(pos_vec,
                rot_vec,
                for_vec)
    }

    override fun DeserializeResponse(bytes: ByteArray): Response {
        val text = String(bytes)
        val parts = text.split(",")

        return Response(parts[0].toInt(), parts[1].toBoolean())
    }

}

