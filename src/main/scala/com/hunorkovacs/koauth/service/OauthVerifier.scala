package com.hunorkovacs.koauth.service

import javax.crypto.Mac
import java.nio.charset.Charset
import javax.crypto.spec.SecretKeySpec
import java.util.{Formatter, TimeZone, Calendar, Base64}
import org.omg.IOP.Encoding
import sun.misc.BASE64Encoder

import scala.concurrent.{ExecutionContext, Future}
import com.hunorkovacs.koauth.domain.EnhancedRequest
import com.hunorkovacs.koauth.service.OauthCombiner._
import com.hunorkovacs.koauth.domain.OauthParams._
import com.hunorkovacs.koauth.service.OauthExtractor.UTF8

object OauthVerifier {

  private val HmacSha1Algorithm = "HmacSHA1"
  private val HmacReadable = "HMAC-SHA1"
  private val TimePrecisionMillis = 10 * 60 * 1000
  private val UTF8Charset = Charset.forName(UTF8)
  private val Base64Encoder = Base64.getEncoder
  private val Format = "%02x"
  private val Calendar1 = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

  val MessageInvalidToken = "Token with Consumer Key does not exist."
  val MessageInvalidSignature = "Signature does not match."
  val MessageInvalidNonce = "Nonce was already used."
  val MessageInvalidTimestamp = "Timestamp falls outside the tolerated interval."
  val MessageUnsupportedMethod = "Unsupported Signature Method."

  def verifyForRequestToken(enhancedRequest: EnhancedRequest)
            (implicit persistence: OauthPersistence, ec: ExecutionContext): Future[Verification] = {
    Future(enhancedRequest.oauthParamsMap(consumerKeyName))
      .flatMap(persistence.getConsumerSecret)
      .flatMap {
        case None => Future(VerificationFailed(MessageInvalidToken))
        case Some(consumerSecret) =>
          val signatureF = verifySignature(enhancedRequest, consumerSecret, tokenSecret = "")
          val algorithmF = verifyAlgorithm(enhancedRequest)
          val timestampF = verifyTimestamp(enhancedRequest)
          val nonceF = verifyNonce(enhancedRequest, "")
          Future.sequence(List(signatureF, algorithmF, timestampF, nonceF)) map { list =>
            list.collectFirst({ case nok: VerificationNok => nok })
              .getOrElse(VerificationOk)
          }
      }
  }

  def verifyWithToken(enhancedRequest: EnhancedRequest)
                     (implicit persistence: OauthPersistence, ec: ExecutionContext): Future[Verification] = {
    val tokenF = Future(enhancedRequest.oauthParamsMap(tokenName))
    (for {
      consumerKey <- Future(enhancedRequest.oauthParamsMap(consumerKeyName))
      token <- tokenF
      secret <- persistence.getTokenSecret(consumerKey, token)
    } yield secret) flatMap {
      case None => Future(VerificationFailed(MessageInvalidToken))
      case Some(secret) =>
        tokenF flatMap { token =>
          val signatureF = verifySignature(enhancedRequest, secret, token)
          val algorithmF = verifyAlgorithm(enhancedRequest)
          val timestampF = verifyTimestamp(enhancedRequest)
          val nonceF = verifyNonce(enhancedRequest, "")
          Future.sequence(List(signatureF, algorithmF, timestampF, nonceF)) map { list =>
            list.collectFirst({ case nok: VerificationNok => nok })
              .getOrElse(VerificationOk)
          }
        }
    }
  }

  def verifySignature(enhancedRequest: EnhancedRequest, consumerSecret: String, tokenSecret: String)
                     (implicit ec: ExecutionContext): Future[Verification] = {
    for {
      signatureBase <- concatItemsForSignature(enhancedRequest)
      expectedSignature <- sign(signatureBase, consumerSecret, tokenSecret)
    } yield {
      val actualSignature = enhancedRequest.oauthParamsMap(signatureName)
      if (actualSignature.equals(expectedSignature)) VerificationOk
      else VerificationFailed(MessageInvalidSignature)
    }
  }

  def verifyNonce(enhancedRequest: EnhancedRequest, token: String)
                 (implicit persistence: OauthPersistence, ec: ExecutionContext): Future[Verification] = {
    Future {
      val nonce = enhancedRequest.oauthParamsMap(nonceName)
      val consumerKey = enhancedRequest.oauthParamsMap(consumerKeyName)
      (nonce, consumerKey)
    } flatMap { t =>
      persistence.nonceExists(t._1, t._2, token)
    } map { exists =>
      if (exists) VerificationFailed(MessageInvalidNonce)
      else VerificationOk
    }
  }

  def verifyTimestamp(enhancedRequest: EnhancedRequest)
                              (implicit ec: ExecutionContext): Future[Verification] = {
    Future {
      val timestamp = enhancedRequest.oauthParamsMap(timestampName)
      try {
        val actualStamp = timestamp.toLong
        val expectedStamp = Calendar1.getTimeInMillis
        if (Math.abs(actualStamp - expectedStamp) <= TimePrecisionMillis) VerificationOk
        else VerificationFailed(MessageInvalidTimestamp)
      } catch {
        case nfEx: NumberFormatException => VerificationUnsupported("Invalid timestamp format.")
      }
    }
  }

  def verifyAlgorithm(enhancedRequest: EnhancedRequest)
                     (implicit ec: ExecutionContext): Future[Verification] = {
    Future {
      val signatureMethod = enhancedRequest.oauthParamsMap(signatureMethodName)
      if (HmacReadable != signatureMethod) VerificationUnsupported(MessageUnsupportedMethod)
      else VerificationOk
    }
  }

  def sign(base: String, consumerSecret: String, tokenSecret: String)
                   (implicit ec: ExecutionContext): Future[String] = {
    def somasoma(bytes: Array[Byte]) = {
      val hash = new StringBuffer()
      for (b <- bytes) {
        val hex = Integer.toHexString(0xFF & b)
        if (hex.length() == 1) hash.append('0')
        hash.append(hex)
      }
      hash.toString
    }

    Future {
      val key = encodeConcat(List(consumerSecret, tokenSecret))
      val signingKey = new SecretKeySpec(key.getBytes(UTF8Charset), HmacSha1Algorithm)
      val mac = Mac.getInstance(HmacSha1Algorithm)
      mac.init(signingKey)
      val bytesToSign = base.getBytes(UTF8Charset)
      val digest = mac.doFinal(bytesToSign)
      val encoder = new BASE64Encoder()
      val signature = encoder.encode(digest)
//      digest.head.to
//      val hexDigest = somasoma(digest)
//      val digest64 = Base64Encoder.encode()
//      new String(digest64, UTF8Charset)
//      digest.toString
      signature
    }
  }


}
