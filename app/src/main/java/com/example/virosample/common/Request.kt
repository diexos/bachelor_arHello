package common

import com.viro.core.Vector

class Request(val position: Vector,
              val rotation: Vector,
              val forward: Vector) {

    override fun toString(): String {
        return "$position,$rotation,$forward"
    }
}