package com.hunorkovacs.koauth.service

import com.hunorkovacs.koauth.domain.{EnhancedRequest, OauthRequest}
import com.hunorkovacs.koauth.service.OauthExtractor.{enhanceRequest, extractAllOauthParams, urlDecode}
import com.hunorkovacs.koauth.service.OauthExtractorSpec._
import org.specs2.mutable._

class OauthExtractorSpec extends Specification {

  val Url = "http://github.com/kovacshuni/koauth"
  val Method = "http://github.com/kovacshuni/koauth"
  val HeaderWithSpace = "OAuth oauth_consumer_key=\"xvz1evFS4wEEPTGEFPHBog\", oauth_nonce=\"kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg\", oauth_signature=\"tnnArxj06cWHq44gCs1OSKk%2FjLY%3D\", oauth_signature_method=\"HMAC-SHA1\", oauth_timestamp=\"1318622958\", oauth_token=\"370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb\", oauth_version=\"1.0\""
  val HeaderWithoutSpace = "OAuth oauth_consumer_key=\"xvz1evFS4wEEPTGEFPHBog\",oauth_nonce=\"kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg\",oauth_signature=\"tnnArxj06cWHq44gCs1OSKk%2FjLY%3D\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"1318622958\",oauth_token=\"370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb\",oauth_version=\"1.0\""
  val ParamsList = List(("oauth_consumer_key", "xvz1evFS4wEEPTGEFPHBog"),
    ("oauth_nonce", "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg"),
    ("oauth_signature", "tnnArxj06cWHq44gCs1OSKk/jLY="),
    ("oauth_signature_method", "HMAC-SHA1"),
    ("oauth_timestamp", "1318622958"),
    ("oauth_token", "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb"),
    ("oauth_version", "1.0"))

  "URL decoding" should {
    "convert normal characters" in {
      urlDecode(NormalCharacters) must equalTo (NormalCharacters)
    }
    "convert illegal characters" in {
      urlDecode(IllegalCharactersEncoded) must equalTo (IllegalCharacters)
    }
    "convert characters on two bytes" in {
      urlDecode(DoubleByteCharactersEncoded) must equalTo (DoubleByteCharacters)
    }
  }

  "Extracting OAuth params" should {
    "extract normal parameters separated with commas&spaces" in {
      val request = OauthRequest(HeaderWithSpace, Url, Method)
      extractAllOauthParams(request) must equalTo(ParamsList).await
    }
    "extract normal parameters sepatated by commas" in {
      val request = OauthRequest(HeaderWithoutSpace, Url, Method)
      extractAllOauthParams(request) must equalTo(ParamsList).await
    }
    "extract empty values" in {
      val request = OauthRequest("OAuth oauth_token=\"\"", Url, Method)
      extractAllOauthParams(request) must equalTo(List(("oauth_token", ""))).await
    }
    "extract totally empty header" in {
      val request = OauthRequest("", Url, Method)
      extractAllOauthParams(request) must equalTo(List.empty[(String, String)]).await
    }
    "discard irregular words" in {
      val request = OauthRequest("Why is this here,oauth_token=\"123\",And this?", Url, Method)
      extractAllOauthParams(request) must equalTo(List(("oauth_token", "123"))).await
    }
  }

  "Enhancing requests" should {
    "enhance request with params" in {
      val request = OauthRequest(HeaderWithSpace, Url, Method)
      enhanceRequest(request) must equalTo(
        EnhancedRequest(HeaderWithSpace,
        Url,
        Method,
        ParamsList,
        ParamsList.toMap)).await
    }
  }
}

object OauthExtractorSpec {

  val NormalCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
  val IllegalCharacters = " !\"#$%&\'()*+,/:;<=>?@"
  val IllegalCharactersEncoded = "%20%21%22%23%24%25%26%27%28%29%2A%2B%2C%2F%3A%3B%3C%3D%3E%3F%40"
  val DoubleByteCharacters = "áéő"
  val DoubleByteCharactersEncoded = "%C3%A1%C3%A9%C5%91"
}
