// SPDX-License-Identifier: Apache-2.0
package org.hiero.block.node.spi.blockmessaging;

/**
 * Interface for handling block items.
 */
public interface BlockItemHandler {
    /**
     * Handle a list of block items. Always called on handler thread. Each registered handler will have its own virtual
     * thread.
     *
     * @param blockItems the immutable list of block items to handle
     */
    void handleBlockItemsReceived(BlockItems blockItems);
}
