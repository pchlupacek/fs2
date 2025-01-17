package fs2

import scodec.bits.ByteVector
import scodec.bits.BitVector

/**
 * Chunk represents a strict, in-memory sequence of `A` values.
 */
trait Chunk[+A] { self =>
  def size: Int
  def uncons: Option[(A, Chunk[A])] =
    if (size == 0) None
    else Some(apply(0) -> drop(1))
  def apply(i: Int): A
  def drop(n: Int): Chunk[A]
  def take(n: Int): Chunk[A]
  def filter(f: A => Boolean): Chunk[A]
  def foldLeft[B](z: B)(f: (B,A) => B): B
  def foldRight[B](z: B)(f: (A,B) => B): B
  def indexWhere(p: A => Boolean): Option[Int] = {
    val index = iterator.indexWhere(p)
    if (index < 0) None else Some(index)
  }
  def isEmpty = size == 0
  def toList = foldRight(Nil: List[A])(_ :: _)
  def toVector = foldLeft(Vector.empty[A])(_ :+ _)
  def collect[B](pf: PartialFunction[A,B]): Chunk[B] = {
    val buf = new collection.mutable.ArrayBuffer[B](size)
    iterator.collect(pf).copyToBuffer(buf)
    Chunk.indexedSeq(buf)
  }
  def map[B](f: A => B): Chunk[B] = {
    val buf = new collection.mutable.ArrayBuffer[B](size)
    iterator.map(f).copyToBuffer(buf)
    Chunk.indexedSeq(buf)
  }
  def mapAccumulate[S,B](s0: S)(f: (S,A) => (S,B)): (S,Chunk[B]) = {
    val buf = new collection.mutable.ArrayBuffer[B](size)
    var s = s0
    for { c <- iterator } {
      val (newS, newC) = f(s, c)
      buf += newC
      s = newS
    }
    (s, Chunk.indexedSeq(buf))
  }
  def scanLeft[B](z: B)(f: (B, A) => B): Chunk[B] = {
    val buf = new collection.mutable.ArrayBuffer[B](size + 1)
    iterator.scanLeft(z)(f).copyToBuffer(buf)
    Chunk.indexedSeq(buf)
  }
  def iterator: Iterator[A] = new Iterator[A] {
    var i = 0
    def hasNext = i < self.size
    def next = { val result = apply(i); i += 1; result }
  }
  override def toString = toList.mkString("Chunk(", ", ", ")")
  override def equals(a: Any) = a.asInstanceOf[Chunk[A]].toList == toList
  override def hashCode = iterator.toStream.hashCode
}

object Chunk {
  val empty: Chunk[Nothing] = new Chunk[Nothing] {
    def size = 0
    def apply(i: Int) = throw new IllegalArgumentException(s"Chunk.empty($i)")
    def drop(n: Int) = empty
    def filter(f: Nothing => Boolean) = empty
    def take(n: Int) = empty
    def foldLeft[B](z: B)(f: (B,Nothing) => B): B = z
    def foldRight[B](z: B)(f: (Nothing,B) => B): B = z
  }

  def singleton[A](a: A): Chunk[A] = new Chunk[A] { self =>
    def size = 1
    def apply(i: Int) = if (i == 0) a else throw new IllegalArgumentException(s"Chunk.singleton($i)")
    def drop(n: Int) = if (n > 0) empty else self
    def filter(f: A => Boolean) = if (f(a)) self else empty
    def take(n: Int) = if (n > 0) self else empty
    def foldLeft[B](z: B)(f: (B,A) => B): B = f(z,a)
    def foldr[B](z: => B)(f: (A,=>B) => B): B = f(a,z)
    def foldRight[B](z: B)(f: (A,B) => B): B = f(a,z)
  }

  def indexedSeq[A](a: collection.IndexedSeq[A]): Chunk[A] = new Chunk[A] {
    def size = a.size
    override def isEmpty = a.isEmpty
    override def uncons = if (a.isEmpty) None else Some(a.head -> indexedSeq(a drop 1))
    def apply(i: Int) = a(i)
    def drop(n: Int) = indexedSeq(a.drop(n))
    def filter(f: A => Boolean) = indexedSeq(a.filter(f))
    def take(n: Int) = indexedSeq(a.take(n))
    def foldLeft[B](z: B)(f: (B,A) => B): B = a.foldLeft(z)(f)
    def foldRight[B](z: B)(f: (A,B) => B): B =
      a.reverseIterator.foldLeft(z)((b,a) => f(a,b))
    override def iterator = a.iterator
  }

  def seq[A](a: Seq[A]): Chunk[A] = new Chunk[A] {
    lazy val vec = a.toIndexedSeq
    def size = a.size
    override def isEmpty = a.isEmpty
    override def uncons = if (a.isEmpty) None else Some(a.head -> seq(a drop 1))
    def apply(i: Int) = vec(i)
    def drop(n: Int) = seq(a.drop(n))
    def filter(f: A => Boolean) = seq(a.filter(f))
    def take(n: Int) = seq(a.take(n))
    def foldLeft[B](z: B)(f: (B,A) => B): B = a.foldLeft(z)(f)
    def foldRight[B](z: B)(f: (A,B) => B): B =
      a.reverseIterator.foldLeft(z)((b,a) => f(a,b))
    override def iterator = a.iterator
  }

  case class Bytes(bs: ByteVector) extends Chunk[Byte] {
    def size = bs.size
    override def isEmpty = bs.isEmpty
    override def uncons = if (bs.isEmpty) None else Some(bs.head -> Bytes(bs drop 1))
    def apply(i: Int) = bs(i)
    def drop(n: Int) = Bytes(bs.drop(n))
    def filter(f: Byte => Boolean) = Bytes(ByteVector(bs.toIterable.filter(f)))
    def take(n: Int) = Bytes(bs.take(n))
    def foldLeft[B](z: B)(f: (B,Byte) => B): B = bs.foldLeft(z)(f)
    def foldRight[B](z: B)(f: (Byte,B) => B): B =
      bs.foldRight(z)(f)
  }

  case class Bits(bs: BitVector) extends Chunk[Boolean] {
    val size = bs.intSize.getOrElse(sys.error("size too big for Int: " + bs.size))
    override def isEmpty = bs.isEmpty
    override def uncons = if (bs.isEmpty) None else Some(bs.head -> Bits(bs drop 1))
    def apply(i: Int) = bs(i)
    def drop(n: Int) = Bits(bs.drop(n))
    def filter(f: Boolean => Boolean) =
      Bits(foldLeft(BitVector.empty)((acc, b) => if (f(b)) acc :+ b else acc))
    def take(n: Int) = Bits(bs.take(n))
    def foldLeft[B](z: B)(f: (B,Boolean) => B): B =
      (0 until size).foldLeft(z)((z,i) => f(z, bs get i))
    def foldRight[B](z: B)(f: (Boolean,B) => B): B =
      ((size-1) to 0 by -1).foldLeft(z)((tl,hd) => f(bs get hd, tl))
  }

  def doubles(values: Array[Double]): Chunk[Double] =
    new Doubles(values, 0, values.length)

  class Doubles(values: Array[Double], offset: Int, sz: Int) extends Chunk[Double] {
  self =>
    val size = sz min (values.length - offset)
    def at(i: Int): Double = values(offset + i)
    def apply(i: Int) = values(offset + i)
    def drop(n: Int) =
      if (n >= size) empty
      else new Doubles(values, offset + n, size - n)
    def filter(f: Double => Boolean) = {
      val arr = values.iterator.slice(offset, offset + sz).filter(f).toArray
      new Doubles(arr, 0, arr.length)
    }
    def take(n: Int) =
      if (n >= size) self
      else new Doubles(values, offset, n)
    def foldLeft[B](z: B)(f: (B,Double) => B): B =
      (0 until size).foldLeft(z)((z,i) => f(z, at(i)))
    def foldRight[B](z: B)(f: (Double,B) => B): B =
      ((size-1) to 0 by -1).foldLeft(z)((tl,hd) => f(at(hd), tl))
  }
}
