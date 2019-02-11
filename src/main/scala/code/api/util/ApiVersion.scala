package code.api.util

import code.api.Constant.ApiPathZero

sealed trait ApiVersion {
  def dottedApiVersion() : String = this.toString.replace("_", ".").replace("v","")
  def vDottedApiVersion() : String = this.toString.replace("_", ".")
  def noV() : String = this.toString.replace("v", "").replace("V","")
  override def toString() = {
    val (head, tail) = getClass().getSimpleName.splitAt(1)
    head.toLowerCase() + tail
  }
}

/**
  * 
  * @param urlPrefix : eg: `obp` or 'berlin-group`` 
  * @param apiStandard eg: obp or `BG` or `UK`
  * @param apiShortVersion eg: `v1.2.1` or `v2.0`
  *                     
  */
case class ScannedApiVersion(urlPrefix: String, apiStandard: String, apiShortVersion: String) extends ApiVersion{
  
  val fullyQualifiedVersion = s"${apiStandard.toUpperCase}$apiShortVersion"
  
  override def toString() = apiShortVersion
}

object ApiVersion {
  
  //Special versions
  case class ImporterApi() extends ApiVersion
  lazy val importerApi = ImporterApi()
  case class AccountsApi() extends ApiVersion
  lazy val accountsApi = AccountsApi()
  case class BankMockApi() extends ApiVersion
  lazy val bankMockApi = BankMockApi()
  
  //OBP Standard 
  val v1_2_1 = ScannedApiVersion(ApiPathZero,"obp","v1.2.1")
  val v1_3_0 = ScannedApiVersion(ApiPathZero,"obp","v1.3.0") 
  val v1_4_0 = ScannedApiVersion(ApiPathZero,"obp","v1.4.0") 
  val v2_0_0 = ScannedApiVersion(ApiPathZero,"obp","v2.0.0") 
  val v2_1_0 = ScannedApiVersion(ApiPathZero,"obp","v2.1.0") 
  val v2_2_0 = ScannedApiVersion(ApiPathZero,"obp","v2.2.0") 
  val v3_0_0 = ScannedApiVersion(ApiPathZero,"obp","v3.0.0") 
  val v3_1_0 = ScannedApiVersion(ApiPathZero,"obp","v3.1.0") 

  case class BerlinGroupV1()  extends ApiVersion {
    override def toString() = "v1"
    //override def toString() = "berlin_group_v1" // TODO don't want to confuse with OBP
  }
  lazy val berlinGroupV1 = BerlinGroupV1()
  case class UKOpenBankingV200()  extends ApiVersion {
    override def toString() = "v2_0"
    // override def toString() = "uk_v2.0.0" // TODO don't want to confuse with OBP
  }
  lazy val ukOpenBankingV200 = UKOpenBankingV200()
  case class OpenIdConnect1() extends ApiVersion
  lazy val openIdConnect1 = OpenIdConnect1()
  case class Sandbox() extends ApiVersion
  lazy val sandbox = Sandbox()
  
  case class APIBuilder() extends ApiVersion {
    override def toString() = "b1"
    //override def toString() = "api_builder_v1" // TODO don't want to confuse with OBP
  }
  lazy val apiBuilder = APIBuilder()


  private val versions =
      v1_2_1 ::
      v1_3_0 ::
      v1_4_0 ::
      v2_0_0 ::
      v2_1_0 ::
      v2_2_0 ::
      v3_0_0 ::
      v3_1_0 ::
      importerApi ::
      accountsApi ::
      bankMockApi ::
      openIdConnect1 ::
      sandbox ::
      berlinGroupV1 ::
      ukOpenBankingV200 ::
      apiBuilder::
      ScannedApis.versionMapScannedApis.keysIterator.toList

  def valueOf(value: String): ApiVersion = {
    
    //This `match` is used for compatibility. Before we do not take care for the BerlinGroup and UKOpenBanking versions carefully. 
    // eg: v1 ==BGv1, v1.3 ==BGv1.3, v2.0 == UKv2.0
    // Now, we use the BerlinGroup standard version in OBP. But we need still make sure old version system is working.
    val compatibilityVersion = value match {
      case v1_2_1.fullyQualifiedVersion => v1_2_1.apiShortVersion
      case v1_3_0.fullyQualifiedVersion => v1_3_0.apiShortVersion
      case v1_4_0.fullyQualifiedVersion => v1_4_0.apiShortVersion
      case v2_0_0.fullyQualifiedVersion => v2_0_0.apiShortVersion
      case v2_1_0.fullyQualifiedVersion => v2_1_0.apiShortVersion
      case v2_2_0.fullyQualifiedVersion => v2_2_0.apiShortVersion
      case v3_0_0.fullyQualifiedVersion => v3_0_0.apiShortVersion
      case v3_1_0.fullyQualifiedVersion => v3_1_0.apiShortVersion
      case _=> value
    }
    
    versions.filter(_.vDottedApiVersion == compatibilityVersion) match {
      case x :: Nil => x // We find exactly one Role
      case x :: _ => throw new Exception("Duplicated version: " + x) // We find more than one Role
      case _ => throw new IllegalArgumentException("Incorrect ApiVersion value: " + value) // There is no Role
    }
  }


}