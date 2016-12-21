package strawman.collection.immutable

import strawman.collection.mutable.{ArrayBuffer, ArrayBufferView, Iterator}
import strawman.collection.{Iterable, IterableFactory, IterableOnce, IndexedSeq, Seq, SeqLike}

import scala.{AnyRef, Boolean, Int}
import scala.Predef.{???, intWrapper}

/**
  * An immutable array.
  *
  * Supports efficient indexed access and has a small memory footprint.
  */
class Array[+A] private (private val elements: scala.Array[AnyRef]) extends IndexedSeq[A] with SeqLike[A, Array] {

  def length: Int = elements.length

  override def knownSize: Int = elements.length

  def apply(i: Int): A = elements(i).asInstanceOf[A]

  def iterator(): Iterator[A] = view.iterator()

  def map[B](f: A => B): Array[B] = Array.tabulate(length)(i => f(apply(i)))

  def flatMap[B](f: A => IterableOnce[B]): Array[B] = Array.fromIterable(View.FlatMap(coll, f))

  def ++[B >: A](xs: IterableOnce[B]): Array[B] =
    xs match {
      case bs: Array[B] =>
        val dest = scala.Array.ofDim[AnyRef](length + bs.length)
        java.lang.System.arraycopy(elements, 0, dest, 0, length)
        java.lang.System.arraycopy(bs.elements, 0, dest, length, bs.length)
        new Array(dest)
      case _ =>
        Array.fromIterable(View.Concat(coll, xs))
    }

  def zip[B](xs: IterableOnce[B]): Array[(A, B)] =
    xs match {
      case bs: Array[B] =>
        Array.tabulate(length min bs.length) { i =>
          (apply(i), bs(i))
        }
      case _ =>
        Array.fromIterable(View.Zip(coll, xs))
    }

  def filter(p: A => Boolean): Array[A] = Array.fromIterable(View.Filter(coll, p))

  def partition(p: A => Boolean): (Array[A], Array[A]) = {
    val pn = View.Partition(coll, p)
    (Array.fromIterable(pn.left), Array.fromIterable(pn.right))
  }

  def take(n: Int): Array[A] = Array.tabulate(n)(apply)

  def drop(n: Int): Array[A] = Array.tabulate((length - n) max 0)(i => apply(n + i))

  def tail: Array[A] =
    if (length > 0) Array.tabulate(length - 1)(i => apply(i + 1))
    else ???

  def reverse: Array[A] = Array.tabulate(length)(i => apply(length - 1 - i))
}

object Array extends IterableFactory[Array] {

  def fromIterable[A](it: Iterable[A]): Array[A] =
    new Array(ArrayBuffer.fromIterable(it).asInstanceOf[ArrayBuffer[AnyRef]].toArray)

  def fill[A](n: Int)(elem: => A): Array[A] = tabulate(n)(_ => elem)

  def tabulate[A](n: Int)(f: Int => A): Array[A] = {
    val elements = scala.Array.ofDim[AnyRef](n)
    var i = 0
    while (i < n) {
      elements(i) = f(i).asInstanceOf[AnyRef]
      i = i + 1
    }
    new Array(elements)
  }

}