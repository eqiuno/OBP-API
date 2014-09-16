/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.model

import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.Set
import net.liftweb.json.JObject
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JArray
import net.liftweb.common._
import code.metadata.comments.Comments
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.bankconnectors.{OBPQueryParam, Connector}
import code.views.Views
import code.metadata.narrative.Narrative
import code.metadata.counterparties.Counterparties


case class TransactionId(val value : String) {
  override def toString = value
}

object TransactionId {
  def unapply(id : String) = Some(TransactionId(id))
}

case class AccountId(val value : String) {
  override def toString = value
}

object AccountId {
  def unapply(id : String) = Some(AccountId(id))
}

case class BankId(val value : String) {
  override def toString = value
}

object BankId {
  def unapply(id : String) = Some(BankId(id))
}

class Bank(
  val id: BankId,
  val shortName : String,
  val fullName : String,
  val logoURL : String,
  val website : String
)
{

  def accounts(user : Box[User]) : Box[List[BankAccount]] = {
    Views.views.vend.getAllAccountsUserCanSee(this, user)
  }

  //This was the behaviour in v1.2 and earlier which has since been changed
  @deprecated
  def accountv12AndBelow(user: Box[User]) : Box[List[BankAccount]] = {
    user match {
      case Full(u) => {
        nonPublicAccounts(u)
      }
      case _ => {
        Full(publicAccounts)
      }
    }
  }

  def publicAccounts : List[BankAccount] = Views.views.vend.getPublicBankAccounts(this)
  def nonPublicAccounts(user : User) : Box[List[BankAccount]] = {
    Views.views.vend.getNonPublicBankAccounts(user, id)
  }

  @deprecated("json generation handled elsewhere as it changes from api version to api version")
  def detailedJson : JObject = {
    ("name" -> shortName) ~
    ("website" -> "") ~
    ("email" -> "")
  }

  @deprecated("json generation handled elsewhere as it changes from api version to api version")
  def toJson : JObject = {
    ("alias" -> id.value) ~
      ("name" -> shortName) ~
      ("logo" -> "") ~
      ("links" -> linkJson)
  }

  @deprecated("json generation handled elsewhere as it changes from api version to api version")
  def linkJson : JObject = {
    ("rel" -> "bank") ~
    ("href" -> {"/" + id + "/bank"}) ~
    ("method" -> "GET") ~
    ("title" -> {"Get information about the bank identified by " + id})
  }
}

object Bank {
  def apply(bankId: BankId) : Box[Bank] = {
    Connector.connector.vend.getBank(bankId)
  }

  def all : List[Bank] = Connector.connector.vend.getBanks

  @deprecated("json generation handled elsewhere as it changes from api version to api version")
  def toJson(banks: Seq[Bank]) : JArray =
    banks.map(bank => bank.toJson)

}

class AccountOwner(
  val id : String,
  val name : String
)

class BankAccount(
  val accountId : AccountId,
  val owners : Set[AccountOwner],
  val accountType : String,
  val balance : BigDecimal,
  val currency : String,
  val name : String,
  val label : String,
  val nationalIdentifier : String,
  val swift_bic : Option[String],
  val iban : Option[String],
  val number : String,
  val bankName : String,
  val bankId : BankId
) extends Loggable{

  private def viewNotAllowed(view : View ) = Failure("user does not have access to the " + view.name + " view")

  def permittedViews(user: Box[User]) : List[View] = {
    user match {
      case Full(u) => u.permittedViews(this)
      case _ =>{
        logger.info("no user was found in the permittedViews")
        publicViews
      }
    }
  }

  /**
  * @param view the view that we want test the access to
  * @param user the user that we want to see if he has access to the view or not
  * @return true if the user is allowed to access this view, false otherwise
  */
  def authorizedAccess(view: View, user: Option[User]) : Boolean = {
    if(view.isPublic)
      true
    else
      user match {
        case Some(u) => u.permittedView(view, this)
        case _ => false
      }
  }

  /**
  * @param user a user requesting to see the other users' permissions
  * @return a Box of all the users' permissions of this bank account if the user passed as a parameter has access to the owner view (allowed to see this kind of data)
  */
  def permissions(user : User) : Box[List[Permission]] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      Views.views.vend.permissions(this)
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + accountId, Empty, Empty)
  }

  /**
  * @param user the user requesting to see the other users permissions on this account
  * @param otherUserProvider the authentication provider of the user whose permissions will be retrieved
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) whose permissions will be retrieved
  * @return a Box of the user permissions of this bank account if the user passed as a parameter has access to the owner view (allowed to see this kind of data)
  */
  def permission(user : User, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Permission] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        u <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider)
        p <- Views.views.vend.permission(this, u)
        } yield p
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + accountId, Empty, Empty)
  }

  /**
  * @param user the user that wants to grant another user access to a view on this account
  * @param the id of the view that we want to grant access
  * @param otherUserProvider the authentication provider of the user to whom access to the view will be granted
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the view will be granted
  * @return a Full(true) if everything is okay, a Failure otherwise
  */
  def addPermission(user : User, viewId : String, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        view <- View.fromUrl(viewId, this) //check if the viewId corresponds to a view
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        isSaved <- Views.views.vend.addPermission(view, otherUser) ?~ "could not save the privilege"
      } yield isSaved
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + accountId, Empty, Empty)
  }

  /**
  * @param user the user that wants to grant another user access to a several views on this account
  * @param the list of views ids that we want to grant access to
  * @param otherUserProvider the authentication provider of the user to whom access to the views will be granted
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the views will be granted
  * @return a the list of the granted views if everything is okay, a Failure otherwise
  */
  def addPermissions(user : User, viewIds : List[String], otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[List[View]] = {
    //we try to get all the views that correspond to that list of view ids
    lazy val viewBoxes = viewIds.map(id => View.fromUrl(id, this))
    //we see if the the is Failures
    lazy val failureList = viewBoxes.collect(v => {
      v match {
        case Empty => Empty
        case x : Failure => x
      }
    })

    lazy val viewsFormIds : Box[List[View]] =
      //if no failures then we return the Full views
      if(failureList.isEmpty)
        Full(viewBoxes.flatten)
      else
        //we return just the first failure
        failureList.head

    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        views <- viewsFormIds
        grantedViews <- Views.views.vend.addPermissions(views, otherUser) ?~ "could not save the privilege"
      } yield views
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + accountId, Empty, Empty)
  }

  /**
  * @param user the user that wants to revoke another user's access to a view on this account
  * @param the id of the view that we want to revoke access
  * @param otherUserProvider the authentication provider of the user to whom access to the view will be revoked
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the view will be revoked
  * @return a Full(true) if everything is okay, a Failure otherwise
  */
  def revokePermission(user : User, viewId : String, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        view <- View.fromUrl(viewId, this) //check if the viewId corresponds to a view
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        isRevoked <- Views.views.vend.revokePermission(view, otherUser) ?~ "could not revoke the privilege"
      } yield isRevoked
    else
      Failure("user : " + user.emailAddress + " don't have access to owner view on account " + accountId, Empty, Empty)
  }

  /**
  *
  * @param user the user that wants to revoke another user's access to all views on this account
  * @param otherUserProvider the authentication provider of the user to whom access to all views will be revoked
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to all views will be revoked
  * @return a Full(true) if everything is okay, a Failure otherwise
  */

  def revokeAllPermissions(user : User, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        isRevoked <- Views.views.vend.revokeAllPermission(bankId, accountId, otherUser)
      } yield isRevoked
    else
      Failure("user : " + user.emailAddress + " don't have access to owner view on account " + accountId, Empty, Empty)
  }

  def views(user : User) : Box[List[View]] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        isRevoked <- Views.views.vend.views(this) ?~ "could not get the views"
      } yield isRevoked
    else
      Failure("user : " + user.emailAddress + " don't have access to owner view on account " + accountId, Empty, Empty)
  }

  def createView(userDoingTheCreate : User,v: ViewCreationJSON): Box[View] = {
    if(!userDoingTheCreate.ownerAccess(this)) {
      Failure({"user: " + userDoingTheCreate.idGivenByProvider + " at provider " + userDoingTheCreate.provider + " does not have owner access"})
    } else {
      val view = Views.views.vend.createView(this, v)
      
      if(view.isDefined) {
        logger.info("user: " + userDoingTheCreate.idGivenByProvider + " at provider " + userDoingTheCreate.provider + " created view: " + view.get +
            " for account " + accountId + "at bank " + bankId)
      }
      
      view
    }
  }

  def updateView(userDoingTheUpdate : User, viewPermalink : String, v: ViewUpdateData) : Box[View] = {
    if(!userDoingTheUpdate.ownerAccess(this)) {
      Failure({"user: " + userDoingTheUpdate.idGivenByProvider + " at provider " + userDoingTheUpdate.provider + " does not have owner access"})
    } else {
      val view = Views.views.vend.updateView(this, viewPermalink, v)
      
      if(view.isDefined) {
        logger.info("user: " + userDoingTheUpdate.idGivenByProvider + " at provider " + userDoingTheUpdate.provider + " updated view: " + view.get +
            " for account " + accountId + "at bank " + bankId)
      }
      
      view
    }
  }
    

  def removeView(userDoingTheRemove : User, viewPermalink: String) : Box[Unit] = {
    if(!userDoingTheRemove.ownerAccess(this)) {
      Failure({"user: " + userDoingTheRemove.idGivenByProvider + " at provider " + userDoingTheRemove.provider + " does not have owner access"})
    } else {
      val deleted = Views.views.vend.removeView(viewPermalink, this)
      
      if(deleted.isDefined) {
        logger.info("user: " + userDoingTheRemove.idGivenByProvider + " at provider " + userDoingTheRemove.provider + " deleted view: " + viewPermalink +
            " for account " + accountId + "at bank " + bankId)
      }
      
      deleted
    }
  }
   

  def publicViews : List[View] =
    Views.views.vend.publicViews(this).getOrElse(Nil)

  def moderatedTransaction(transactionId: TransactionId, view: View, user: Box[User]) : Box[ModeratedTransaction] = {
    if(authorizedAccess(view, user))
      Connector.connector.vend.getTransaction(bankId, accountId, transactionId).map(view.moderate)
    else
      viewNotAllowed(view)
  }

  def getModeratedTransactions(user : Box[User], view : View, queryParams: OBPQueryParam*): Box[List[ModeratedTransaction]] = {
    if(authorizedAccess(view, user)) {
      for {
        transactions <- Connector.connector.vend.getTransactions(bankId, accountId, queryParams: _*)
      } yield transactions.map(view.moderate)
    }
    else viewNotAllowed(view)
  }

  def moderatedBankAccount(view: View, user: Box[User]) : Box[ModeratedBankAccount] = {
    if(authorizedAccess(view, user))
      //implicit conversion from option to box
      view.moderate(this)
    else
      viewNotAllowed(view)
  }

  /**
  * @param the view that we will use to get the ModeratedOtherBankAccount list
  * @param the user that want access to the ModeratedOtherBankAccount list
  * @return a Box of a list ModeratedOtherBankAccounts, it the bank
  *  accounts that have at least one transaction in common with this bank account
  */
  def moderatedOtherBankAccounts(view : View, user : Box[User]) : Box[List[ModeratedOtherBankAccount]] = {
    if(authorizedAccess(view, user))
      Connector.connector.vend.getModeratedOtherBankAccounts(bankId, accountId)(view.moderate)
    else
      viewNotAllowed(view)
  }
  /**
  * @param the ID of the other bank account that the user want have access
  * @param the view that we will use to get the ModeratedOtherBankAccount
  * @param the user that want access to the otherBankAccounts list
  * @return a Box of a ModeratedOtherBankAccounts, it a bank
  *  account that have at least one transaction in common with this bank account
  */
  def moderatedOtherBankAccount(otherAccountID : String, view : View, user : Box[User]) : Box[ModeratedOtherBankAccount] =
    if(authorizedAccess(view, user))
      Connector.connector.vend.getModeratedOtherBankAccount(bankId, accountId, otherAccountID)(view.moderate)
    else
      viewNotAllowed(view)

  @deprecated("json generation handled elsewhere as it changes from api version to api version")
  def overviewJson(user: Box[User]): JObject = {
    val views = permittedViews(user)
    ("number" -> number) ~
    ("account_alias" -> label) ~
    ("owner_description" -> "") ~
    ("views_available" -> views.map(view => view.toJson)) ~
    View.linksJson(views, accountId, bankId)
  }
}

object BankAccount {
  def apply(bankId: BankId, accountId: AccountId) : Box[BankAccount] = {
    Connector.connector.vend.getBankAccount(bankId, accountId)
  }

  def publicAccounts : List[BankAccount] = {
    Views.views.vend.getAllPublicAccounts
  }

  def accounts(user : Box[User]) : List[BankAccount] = {
    Views.views.vend.getAllAccountsUserCanSee(user)
  }

  def nonPublicAccounts(user : User) : Box[List[BankAccount]] = {
    Views.views.vend.getNonPublicBankAccounts(user)
  }
}

class OtherBankAccount(
  val id : String,
  val label : String,
  val nationalIdentifier : String,
  //the bank international identifier
  val swift_bic : Option[String],
  //the international account identifier
  val iban : Option[String],
  val number : String,
  val bankName : String,
  val kind : String,
  val originalPartyBankId: BankId, //bank id of the party for which this OtherBankAccount is the counterparty
  val originalPartyAccountId: AccountId //account id of the party for which this OtherBankAccount is the counterparty
) {

  val metadata : OtherBankAccountMetadata = {
    Counterparties.counterparties.vend.getOrCreateMetadata(originalPartyBankId, originalPartyAccountId, this)
  }
}

class Transaction(
  //A universally unique id
  val uuid : String,
  //id is unique for transactions of @thisAccount
  val id : TransactionId,
  val thisAccount : BankAccount,
  val otherAccount : OtherBankAccount,
  //E.g. cash withdrawal, electronic payment, etc.
  val transactionType : String,
  val amount : BigDecimal,
  //ISO 4217, e.g. EUR, GBP, USD, etc.
  val currency : String,
  // Bank provided label
  val description : Option[String],
  // The date the transaction was initiated
  val startDate : Date,
  // The date when the money finished changing hands
  val finishDate : Date,
  //the new balance for the bank account
  val balance :  BigDecimal
) {

  val bankId = thisAccount.bankId
  val accountId = thisAccount.accountId

  /**
   * The metadata is set up using dependency injection. If you want to, e.g. override the Comments implementation
   * for a particular scope, use Comments.comments.doWith(NewCommentsImplementation extends Comments{}){
   *   //code in here will use NewCommentsImplementation (e.g. val t = new Transaction(...) will result in Comments.comments.vend
   *   // return NewCommentsImplementation here below)
   * }
   *
   * If you want to change the current default implementation, you would change the buildOne function in Comments to
   * return a different value
   *
   */
  val metadata : TransactionMetadata = new TransactionMetadata(
      Narrative.narrative.vend.getNarrative(bankId, accountId, id) _,
      Narrative.narrative.vend.setNarrative(bankId, accountId, id) _,
      Comments.comments.vend.getComments(bankId, accountId, id) _,
      Comments.comments.vend.addComment(bankId, accountId, id) _,
      Comments.comments.vend.deleteComment(bankId, accountId, id) _,
      Tags.tags.vend.getTags(bankId, accountId, id) _,
      Tags.tags.vend.addTag(bankId, accountId, id) _,
      Tags.tags.vend.deleteTag(bankId, accountId, id) _,
      TransactionImages.transactionImages.vend.getImagesForTransaction(bankId, accountId, id) _,
      TransactionImages.transactionImages.vend.addTransactionImage(bankId, accountId, id) _,
      TransactionImages.transactionImages.vend.deleteTransactionImage(bankId, accountId, id) _,
      WhereTags.whereTags.vend.getWhereTagsForTransaction(bankId, accountId, id) _,
      WhereTags.whereTags.vend.addWhereTag(bankId, accountId, id) _,
      WhereTags.whereTags.vend.deleteWhereTag(bankId, accountId, id) _
    )
}