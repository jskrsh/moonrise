package net.mythicisland.moonrise.parser

import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Modifying
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.mythicisland.moonrise.ArgumentFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class MarkerTagResolver(private val resolver: TagResolver) : TagResolver {

    private val created = AtomicInteger()

    fun count(): Int = created.get()

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        if (resolver.has(name)) {
            return null
        }
        val (placeholder, format) = Placeholders.placeholder(name, arguments) ?: return null
        created.incrementAndGet()
        return Tag.selfClosingInserting(Placeholders.marker(placeholder, format))
    }

    override fun has(name: String): Boolean = !resolver.has(name)
}

internal class ModifyingTagResolver(private val resolver: TagResolver) : TagResolver {

    private val found = AtomicBoolean(false)

    fun isFound(): Boolean = found.get()

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        val tag = resolver.resolve(name, arguments, context)
        if (tag is Modifying) {
            found.set(true)
        }
        return tag
    }

    override fun has(name: String): Boolean = resolver.has(name)
}

internal class ArgumentTagResolver(private val resolver: TagResolver, private val args: TranslationArguments) : TagResolver {

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        if (resolver.has(name)) {
            return null
        }
        val (placeholder, format) = Placeholders.placeholder(name, arguments) ?: return null
        val value = args.find(placeholder, ArgumentFormat.of(format) ?: ArgumentFormat.Default)
        return Tag.selfClosingInserting(value ?: Placeholders.missing(placeholder))
    }

    override fun has(name: String): Boolean = !resolver.has(name)
}
