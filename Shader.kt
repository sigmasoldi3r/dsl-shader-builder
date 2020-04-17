
import glew.*
import kotlinx.cinterop.*

/**
 * A shader is a GPU compiled program that enhances the render pipeline.
 */
@ExperimentalUnsignedTypes
class Shader(
    private val programId: UInt,
    val uniforms: Map<String, Uniform>
) {
    /**
     * This simple builder holds nullable data used during the compilation of a shader.
     */
    class Builder {
        var vertexSource: String? = null
        var fragmentSource: String? = null
        var tessellationControlSource: String? = null
        var tessellationEvaluationSource: String? = null
        var geometrySource: String? = null
        var computeSource: String? = null
    }
    companion object {
        /**
         * For error reporting only, gets the type of the program in a human readable way.
         */
        fun getTypeOfProgram(type: Int) = when (type) {
            GL_VERTEX_SHADER -> "GL_VERTEX_SHADER"
            GL_FRAGMENT_SHADER -> "GL_FRAGMENT_SHADER"
            GL_TESS_CONTROL_SHADER -> "GL_TESS_CONTROL_SHADER"
            GL_TESS_EVALUATION_SHADER -> "GL_TESS_EVALUATION_SHADER"
            GL_GEOMETRY_SHADER -> "GL_GEOMETRY_SHADER"
            GL_COMPUTE_SHADER -> "GL_COMPUTE_SHADER"
            else -> "UNKNOWN_TYPE[$type]"
        }

        /**
         * Compiles a single program.
         */
        private fun compileProgram(source: String, type: Int): UInt = memScoped {
            val id = glCreateShader!!(type.toUInt())
            glShaderSource!!(id, 1, arrayOf(source).toCStringArray(this), null)
            glCompileShader!!(id)
            val result = alloc<GLintVar> {
                glGetShaderiv!!(id, GL_COMPILE_STATUS.toUInt(), ptr)
            }.value
            if (result == GL_FALSE) {
                val length = alloc<GLintVar> {
                    glGetShaderiv!!(id, GL_INFO_LOG_LENGTH.toUInt(), ptr)
                }.value
                val message = allocArray<ByteVar>(length) {
                    glGetShaderInfoLog!!(id, length, null, ptr)
                }.toKString()
                error("Couldn't compile $type shader: $message")
            }
            return id
        }

        /**
         * Compiles and attaches the shader, if successful.
         */
        private fun compileAndAttach(src: String, programId: UInt, kind: Int) = compileProgram(src, kind).also {
            glAttachShader!!(programId, it)
        }

        /**
         * Builds the shader, returns null if the link stage failed.
         */
        fun building(builderFunction: Builder.() -> Unit): Shader = memScoped {
            val builder = Builder().apply(builderFunction)
            val programId = glCreateProgram!!()
            val vertex = builder.vertexSource?.let { compileAndAttach(it, programId, GL_VERTEX_SHADER) }
            val fragment = builder.fragmentSource?.let { compileAndAttach(it, programId, GL_FRAGMENT_SHADER) }
            val tessellationControl =
                builder.tessellationControlSource?.let { compileAndAttach(it, programId, GL_TESS_CONTROL_SHADER) }
            val tessellationEvaluation =
                builder.tessellationEvaluationSource?.let { compileAndAttach(it, programId, GL_TESS_EVALUATION_SHADER) }
            val geometry = builder.geometrySource?.let { compileAndAttach(it, programId, GL_GEOMETRY_SHADER) }
            val compute = builder.computeSource?.let { compileAndAttach(it, programId, GL_COMPUTE_SHADER) }
            // Link program.
            glLinkProgram!!(programId)
            val result = alloc<GLintVar> {
                glGetProgramiv!!(programId, GL_LINK_STATUS.toUInt(), ptr)
            }.value
            // Check link errors.
            if (result == GL_FALSE) {
                val length = alloc<GLintVar> {
                    glGetProgramiv!!(programId, GL_INFO_LOG_LENGTH.toUInt(), ptr)
                }.value
                val message = allocArray<ByteVar>(length) {
                    glGetProgramInfoLog!!(programId, length, null, ptr)
                }.toKString()
                error(message)
            }
            // Detach & delete on finish:
            vertex?.let {
                glDetachShader!!(programId, it)
                glDeleteShader!!(it)
            }
            fragment?.let {
                glDetachShader!!(programId, it)
                glDeleteShader!!(it)
            }
            tessellationControl?.let {
                glDetachShader!!(programId, it)
                glDeleteShader!!(it)
            }
            tessellationEvaluation?.let {
                glDetachShader!!(programId, it)
                glDeleteShader!!(it)
            }
            geometry?.let {
                glDetachShader!!(programId, it)
                glDeleteShader!!(it)
            }
            compute?.let {
                glDetachShader!!(programId, it)
                glDeleteShader!!(it)
            }
            // Load uniform locations.
            val uniforms = mutableMapOf<String, Uniform>()
            val uniformCount = alloc<IntVar> {
                glGetProgramiv!!(programId, GL_ACTIVE_UNIFORMS.toUInt(), ptr)
            }.value
            val zero = GL_ZERO.toUInt()
            for (i in 0 until uniformCount) {
                val nameLen = alloc<IntVar>()
                val num = alloc<IntVar>()
                val type = alloc<UIntVar> { value = zero }
                val name = allocArray<ByteVar>(1024)
                glGetActiveUniform!!(programId, i.toUInt(), 1023, nameLen.ptr, num.ptr, type.ptr, name)
                val location = glGetUniformLocation!!(programId, name)
                uniforms[name.toKString()] = Uniform(location)
            }
            // Return shader if ok:
            return Shader(programId, uniforms)
        }
    }

    /**
     * Used in pipeline events for binding to the current graphics context.
     */
    fun use(onUse: (Shader.() -> Unit)? = null) {
        glUseProgram!!(programId)
        let(onUse ?: return)
    }
}
