/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.cache.backup;

import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;

import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.distributed.internal.DM;
import org.apache.geode.distributed.internal.ReplyException;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class FlushToDiskOperationTest {

  private DM dm;
  private InternalCache cache;
  private Set<InternalDistributedMember> recipients;

  private InternalDistributedMember sender;

  private FlushToDiskFactory flushToDiskFactory;
  private FlushToDiskProcessor flushToDiskReplyProcessor;
  private FlushToDiskRequest flushToDiskRequest;
  private FlushToDisk flushToDisk;

  private FlushToDiskOperation flushToDiskOperation;

  @Before
  public void setUp() throws Exception {
    dm = mock(DM.class);
    cache = mock(InternalCache.class);

    flushToDiskReplyProcessor = mock(FlushToDiskProcessor.class);
    flushToDiskRequest = mock(FlushToDiskRequest.class);
    flushToDisk = mock(FlushToDisk.class);

    flushToDiskFactory = mock(FlushToDiskFactory.class);

    sender = mock(InternalDistributedMember.class, "sender");
    recipients = new HashSet<>();

    flushToDiskOperation =
        new FlushToDiskOperation(dm, sender, cache, recipients, flushToDiskFactory);

    when(flushToDiskReplyProcessor.getProcessorId()).thenReturn(42);

    when(flushToDiskFactory.createReplyProcessor(eq(dm), eq(recipients)))
        .thenReturn(flushToDiskReplyProcessor);
    when(flushToDiskFactory.createRequest(eq(sender), eq(recipients), eq(42)))
        .thenReturn(flushToDiskRequest);
    when(flushToDiskFactory.createFlushToDisk(eq(cache))).thenReturn(flushToDisk);
  }

  @Test
  public void sendShouldSendFlushToDiskMessage() throws Exception {
    flushToDiskOperation.send();

    verify(dm, times(1)).putOutgoing(flushToDiskRequest);
  }

  @Test
  public void sendShouldHandleCancelExceptionFromWaitForReplies() throws Exception {
    ReplyException replyException =
        new ReplyException("expected exception", new CacheClosedException("expected exception"));
    doThrow(replyException).when(flushToDiskReplyProcessor).waitForReplies();
    flushToDiskOperation.send();
  }

  @Test
  public void sendShouldHandleInterruptedExceptionFromWaitForReplies() throws Exception {
    doThrow(new InterruptedException("expected exception")).when(flushToDiskReplyProcessor)
        .waitForReplies();
    flushToDiskOperation.send();
  }

  @Test(expected = ReplyException.class)
  public void sendShouldThrowReplyExceptionWithNoCauseFromWaitForReplies() throws Exception {
    doThrow(new ReplyException("expected exception")).when(flushToDiskReplyProcessor)
        .waitForReplies();
    flushToDiskOperation.send();
  }

  @Test(expected = ReplyException.class)
  public void sendShouldThrowReplyExceptionWithCauseThatIsNotACancelFromWaitForReplies()
      throws Exception {
    doThrow(new ReplyException("expected exception", new RuntimeException("expected")))
        .when(flushToDiskReplyProcessor).waitForReplies();
    flushToDiskOperation.send();
  }

  @Test
  public void sendShouldProcessLocallyBeforeWaitingForReplies() throws Exception {
    InOrder inOrder = inOrder(flushToDisk, flushToDiskReplyProcessor);
    flushToDiskOperation.send();

    inOrder.verify(flushToDisk, times(1)).run();
    inOrder.verify(flushToDiskReplyProcessor, times(1)).waitForReplies();
  }
}
