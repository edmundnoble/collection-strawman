package strawman
package collection
package immutable

import strawman.collection.mutable.{ArrayBuffer, Builder}
import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.tailrec
import scala.{Any, Nothing, Boolean, Unit, Int, StringBuilder, inline, NoSuchElementException, IndexOutOfBoundsException}
import scala.Predef.???
import mutable.{Builder, ListBuffer}

import scala.annotation.unchecked.uncheckedVariance

/**
  * Stack-ended catenable queue. Supports O(1) append, and (amortized)
  * O(1) `uncons`, such that walking the sequence via N successive `uncons`
  * steps takes O(N). Like a difference list, conversion to a `Seq[A]`
  * takes linear time, regardless of how the sequence is built up.
  * Conversion from a `Seq` takes constant time, but maintaining `uncons`
  * performance in that case depends on the underlying `Seq`'s `uncons` performance.
  *
  * Implementation from fs2.util.Catenable in the Functional Streams for Scala (fs2) project
  */
sealed abstract class Steque[+A]
  extends Seq[A]
    with LinearSeq[A]
    with SeqOps[A, Steque, Steque[A]]
    with Buildable[A, Steque[A]] {

  import Steque._

  override def fromIterable[B](coll: collection.Iterable[B]): Steque[B] = Steque.fromIterable(coll)

  final override def tail: Steque[A] = {
    var c: Steque[A] = this
    val rights = new collection.mutable.ArrayBuffer[Steque[A]]
    while (true) {
      val rightsLength = rights.length
      c match {
        case Empty =>
          if (rightsLength == 0) {
            throw new NoSuchElementException("tail on empty steque")
          } else {
            c = rights(rightsLength - 1)
            rights.remove(rightsLength - 1)
          }
        case Single(_) =>
          return reassociateRights(rights, rightsLength)
        case Append(l, r) =>
          c = l
          rights += r
      }
    }
    ???
  }

  final override def head: A = {
    var c: Steque[A] = this
    val rights = new collection.mutable.ArrayBuffer[Steque[A]]
    while (true) {
      c match {
        case Empty =>
          if (rights.isEmpty) {
            throw new NoSuchElementException("head on empty steque")
          } else {
            val rightsLength = rights.length
            c = rights(rightsLength - 1)
            rights.remove(rightsLength - 1)
          }
        case Single(a) =>
          return a
        case Append(l, r) =>
          c = l
          rights += r
      }
    }
    ???
  }

  final override def concat[B >: A](c: IterableOnce[B]): Steque[B] = c match {
    case (s: Steque[B]) => append(this, s)
    case (s: Seq[B]) => append(this, Steque.fromIterable(s))
    case _ => super.++(c)
  }

  final def ++:[B >: A](c: Steque[B]): Steque[B] = {
    append(this, c)
  }

  final def concat[B >: A](steque: Steque[B]): Steque[B] = append(this, steque)

  /** Returns a new catenable consisting of `a` followed by this. O(1) runtime. */
  final def cons[A2 >: A](a: A2): Steque[A2] =
    if (this eq Empty) single(a)
    else Append(single(a), this)

  /** Alias for [[cons]]. */
  final def +:[A2 >: A](a: A2): Steque[A2] =
    cons(a)

  /** Returns a new catenable consisting of this followed by `a`. O(1) runtime. */
  final def snoc[A2 >: A](a: A2): Steque[A2] =
    if (this eq Empty) single(a)
    else Append(this, single(a))

  /** Alias for [[snoc]]. */
  final def :+[A2 >: A](a: A2): Steque[A2] =
    snoc(a)

  override final def apply(idx: Int): A = {
    var c: Steque[A] = this
    val rights = new collection.mutable.ArrayBuffer[Steque[A]]
    var count = 0
    while (true) {
      val rightsLength = rights.length
      c match {
        case Empty =>
          throw new IndexOutOfBoundsException()
        case Single(a) =>
          if (count == idx) {
            return a
          } else if (rightsLength == 0) {
            throw new IndexOutOfBoundsException()
          } else {
            count += 1
            c = rights(rightsLength - 1)
            rights.remove(rightsLength - 1)
          }
        case Append(l, r) => c = l; rights += r
      }
    }
    ???
  }

  override final def length: Int = {
    var len = 0
    foreach(_ => len += 1)
    len
  }

  override final def foldLeft[B](z: B)(f: (B, A) => B): B = {
    var c: Steque[A] = this
    val rights = new collection.mutable.ArrayBuffer[Steque[A]]
    var result: B = z
    while (c ne null) {
      val rightsLength = rights.length
      c match {
        case Empty =>
          if (rightsLength == 0) {
            c = null
          } else {
            c = rights(rightsLength - 1)
            rights.remove(rightsLength - 1)
          }
        case Single(a) =>
          result = f(result, a)
          if (rightsLength == 0) c = null
          else c = reassociateRights(rights, rightsLength)
        case Append(l, r) => c = l; rights += r
      }
    }
    result
  }

  @inline
  private def foreachHalting(f: A => Boolean): Unit = {
    var c: Steque[A] = this
    val rights = new collection.mutable.ArrayBuffer[Steque[A]]
    while (c ne null) {
      val rightsLength = rights.length
      c match {
        case Empty =>
          if (rightsLength == 0) {
            c = null
          } else {
            c = rights(rightsLength - 1)
            rights.remove(rightsLength - 1)
          }
        case Single(a) =>
          val continue = f(a) && rightsLength > 0
          if (continue) {
            c = reassociateRights(rights, rightsLength)
          } else {
            c = null
          }
        case Append(l, r) => c = l; rights += r
      }
    }
  }

  /** Applies the supplied function to each element, left to right. */
  override final def foreach[U](f: A => U): Unit = {
    var c: Steque[A] = this
    val rights = new collection.mutable.ArrayBuffer[Steque[A]]
    while (c ne null) {
      val rightsLength = rights.length
      c match {
        case Empty =>
          if (rightsLength == 0) {
            c = null
          } else {
            c = rights(rightsLength - 1)
            rights.remove(rightsLength - 1)
          }
        case Single(a) =>
          f(a)
          if (rightsLength > 0) {
            c = reassociateRights(rights, rightsLength)
          } else {
            c = null
          }
        case Append(l, r) => c = l; rights += r
      }
    }
  }

  override final def toString = {
    if (this eq Empty) {
      "Steque()"
    } else {
      val sb = new StringBuilder("Steque(")
      foreach { a => sb ++= a.toString; sb ++= ", " }
      sb.setCharAt(sb.length - 2, ')')
      sb.deleteCharAt(sb.length - 1)
      sb.result()
    }
  }

  override protected[this] def newBuilder: Builder[A, Steque[A]] =
    Steque.newBuilder

  override protected[this] def fromSpecificIterable(coll: collection.Iterable[A]): Steque[A] =
    fromIterable(coll)

}

object Steque extends IterableFactory[Steque] {

  def newBuilder[A]: mutable.Builder[A, Steque[A]] = new mutable.Builder[A, Steque[A]] {
    var current: Steque[A] = empty

    override def add(elem: A): this.type = {
      current = current.snoc(elem)
      this
    }

    override def clear(): Unit =
      current = empty

    override def result: Steque[A] =
      current
  }

  private[Steque] def reassociateRights[A](rights: ArrayBuffer[Steque[A]], length: Int): Steque[A] =
    if (length == 0) {
      empty
    } else {
      var next = rights(0)
      var i = 1
      while (i < length) {
        next = Append(next, rights(i))
        i += 1
      }
      next
    }

  final case object Empty extends Steque[Nothing] {
    override def isEmpty: Boolean = true
  }

  final case class Single[A](a: A) extends Steque[A] {
    override def isEmpty: Boolean = false
  }

  final case class Append[A](left: Steque[A], right: Steque[A]) extends Steque[A] {
    override def isEmpty: Boolean = false // b/c `append` constructor doesn't allow either branch to be empty
  }

  /** Empty catenable. */
  override def empty[A]: Steque[A] = Empty

  /** Creates a catenable of 1 element. */
  def single[A](a: A): Steque[A] = Single(a)

  /** Appends two catenables. */
  def append[A](c: Steque[A], c2: Steque[A]): Steque[A] =
    if (c.isEmpty) {
      if (c2.isEmpty)
        Empty
      else
        c2
    } else if (c2.isEmpty) {
      c
    } else {
      Append(c, c2)
    }

  /** Creates a catenable from the specified sequence. */
  override def fromIterable[A](s: collection.Iterable[A]): Steque[A] =
    if (s.isEmpty) Empty
    else s.foldLeft(Steque.empty[A])((st, sa) => st :+ sa)

}

