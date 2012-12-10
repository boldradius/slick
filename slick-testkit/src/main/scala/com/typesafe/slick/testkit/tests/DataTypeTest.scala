package com.typesafe.slick.testkit.tests

import org.junit.Assert._
import com.typesafe.slick.testkit.util.{TestkitTest, TestDB}
import java.io.{ObjectInputStream, ObjectOutputStream, ByteArrayOutputStream}
import java.sql.{Blob, Date, Time, Timestamp}
import javax.sql.rowset.serial.SerialBlob

class DataTypeTest(val tdb: TestDB) extends TestkitTest {
  import tdb.profile.simple._

  override val reuseInstance = true

  def testByteArray {
    object T extends Table[(Int, Array[Byte])]("test") {
      def id = column[Int]("id")
      def data = column[Array[Byte]]("data")
      def * = id ~ data
    }

    T.ddl.createStatements foreach println
    T.ddl.create;
    T insert (1, Array[Byte](1,2,3))
    T insert (2, Array[Byte](4,5))
    assertEquals(Set((1,"123"), (2,"45")), Query(T).list.map{ case (id, data) => (id, data.mkString) }.toSet)
  }

  def testNumeric {
    object T extends Table[(Int, Int, Long, Short, Byte, Double, Float)]("test2") {
      def id = column[Int]("id")
      def intData = column[Int]("int_data")
      def longData = column[Long]("long_data")
      def shortData = column[Short]("short_data")
      def byteData = column[Byte]("byte_data")
      def doubleData = column[Double]("double_data")
      def floatData = column[Float]("float_data")
      def * = id ~ intData ~ longData ~ shortData ~ byteData ~ doubleData ~ floatData
    }

    T.ddl.createStatements foreach println
    T.ddl.create;

    def test(data: List[(Int, Int, Long, Short, Byte, Double, Float)]) {
      T.insertAll(data: _*)
      val q = T.sortBy(_.id)
      assertEquals(data, q.list)
      Query(T).delete
    }

    test(List(
      ( 2, -1, -1L, -1: Short, -1: Byte, -1.0, -1.0f),
      ( 3,  0,  0L,  0: Short,  0: Byte,  0.0,  0.0f),
      ( 4,  1,  1L,  1: Short,  1: Byte,  1.0,  1.0f)
    ))

    test(List(
      (1, Int.MinValue, 0L, Short.MinValue, Byte.MinValue, 0.0, 0.0f),
      (5, Int.MaxValue, 0L, Short.MaxValue, Byte.MaxValue, 0.0, 0.0f)
    ))

    ifCap(scap.typeLong) {
      test(List(
        (1, 0, Long.MinValue, 0, 0, 0.0, 0.0f),
        (5, 0, Long.MaxValue, 0, 0, 0.0, 0.0f)
      ))
    }
  }

  def testBlob = ifCap(scap.typeBlob) {
    object T extends Table[(Int, Blob)]("test3") {
      def id = column[Int]("id")
      def data = column[Blob]("data")
      def * = id ~ data
    }

    sharedSession.withTransaction {
      T.ddl.create;
      T insert (1, new SerialBlob(Array[Byte](1,2,3)))
      T insert (2, new SerialBlob(Array[Byte](4,5)))

      assertEquals(Set((1,"123"), (2,"45")),
        Query(T).mapResult{ case (id, data) => (id, data.getBytes(1, data.length.toInt).mkString) }.to[Set])
    }
  }

  def testMappedBlob = ifCap(scap.typeBlob) {
    case class Serialized[T](value: T)

    implicit def serializedType[T] = MappedColumnType.base[Serialized[T], Blob]({ s =>
      val b = new ByteArrayOutputStream
      val out = new ObjectOutputStream(b)
      out.writeObject(s.value)
      out.flush
      new SerialBlob(b.toByteArray)
    }, { b =>
      val in = new ObjectInputStream(b.getBinaryStream)
      Serialized[T](in.readObject().asInstanceOf[T])
    })

    object T extends Table[(Int, Serialized[List[Int]])]("t") {
      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
      def b = column[Serialized[List[Int]]]("b")
      def * = id ~ b
    }

    sharedSession.withTransaction {
      T.ddl.create
      T.b.insertAll(Serialized(List(1,2,3)), Serialized(List(4,5)))
      assertEquals(Set((1, Serialized(List(1,2,3))), (2, Serialized(List(4,5)))), Query(T).list.toSet)
    }
  }

  private def roundtrip[T : BaseColumnType](tn: String, v: T) {
    object T1 extends Table[(Int, T)](tn) {
      def id = column[Int]("id")
      def data = column[T]("data")
      def * = id ~ data
    }

    T1.ddl.create
    T1.insert((1, v))
    assertEquals(v, T1.map(_.data).first)
    assertEquals(Some(1), T1.filter(_.data === v).map(_.id).firstOption)
    assertEquals(None, T1.filter(_.data =!= v).map(_.id).firstOption)
  }

  def testDate =
    roundtrip("date_t1", Date.valueOf("2012-12-24"))

  def testTime =
    roundtrip("time_t1", Time.valueOf("17:53:48"))

  def testTimestamp =
    roundtrip[Timestamp]("timestamp_t1", Timestamp.valueOf("2012-12-24 17:53:48.0"))
}
