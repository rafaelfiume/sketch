package org.fiume.sketch.shared.common.events

/*
 * The `Recipient` defines the consumers or consumer groups to which the notification
 * should be exclusively routed.
 */
final case class Recipient(name: String) extends AnyVal
