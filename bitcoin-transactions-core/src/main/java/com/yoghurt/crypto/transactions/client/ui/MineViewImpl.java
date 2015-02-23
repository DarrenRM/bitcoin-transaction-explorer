package com.yoghurt.crypto.transactions.client.ui;

import java.util.Date;
import java.util.Map.Entry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlecode.gwt.crypto.bouncycastle.util.encoders.Hex;
import com.googlecode.gwt.crypto.util.Str;
import com.yoghurt.crypto.transactions.client.util.FormatUtil;
import com.yoghurt.crypto.transactions.client.util.RepeatingExecutor;
import com.yoghurt.crypto.transactions.client.util.block.BlockEncodeUtil;
import com.yoghurt.crypto.transactions.client.util.transaction.ComputeUtil;
import com.yoghurt.crypto.transactions.client.widget.BlockHexViewer;
import com.yoghurt.crypto.transactions.client.widget.HashHexViewer;
import com.yoghurt.crypto.transactions.client.widget.TransactionHexViewer;
import com.yoghurt.crypto.transactions.client.widget.ValueViewer;
import com.yoghurt.crypto.transactions.shared.domain.Block;
import com.yoghurt.crypto.transactions.shared.domain.RawBlockContainer;
import com.yoghurt.crypto.transactions.shared.domain.RawTransactionContainer;
import com.yoghurt.crypto.transactions.shared.domain.TransactionPartType;
import com.yoghurt.crypto.transactions.shared.util.ArrayUtil;
import com.yoghurt.crypto.transactions.shared.util.NumberEncodeUtil;
import com.yoghurt.crypto.transactions.shared.util.NumberParseUtil;

@Singleton
public class MineViewImpl extends Composite implements MineView {
  private static final int MINING_SIMULATION_DELAY = 250;

  interface MineViewImplUiBinder extends UiBinder<Widget, MineViewImpl> {}

  private static final MineViewImplUiBinder UI_BINDER = GWT.create(MineViewImplUiBinder.class);

  @UiField ValueViewer versionViewer;
  @UiField ValueViewer previousBlockHashViewer;
  @UiField ValueViewer merkleRootViewer;
  @UiField ValueViewer timestampViewer;
  @UiField ValueViewer bitsViewer;
  @UiField ValueViewer nonceViewer;

  @UiField BlockHexViewer blockHexViewer;
  @UiField HashHexViewer blockHashViewer;
  @UiField TransactionHexViewer coinbaseHexViewer;

  private final ScheduledCommand defferedTimeHash = new ScheduledCommand() {
    @Override
    public void execute() {
      synchronizeTime();
      doHashCycle();
    }
  };

  private final ScheduledCommand defferedNonceHash = new ScheduledCommand() {
    @Override
    public void execute() {
      incrementNonce();
      doHashCycle();
    }
  };

  private final ScheduledCommand defferedNonceDecrementHash = new ScheduledCommand() {
    @Override
    public void execute() {
      decrementNonce();
      doHashCycle();
    }
  };

  private final ScheduledCommand defferedExtraNonceHash = new ScheduledCommand() {
    @Override
    public void execute() {
      incrementExtraNonce();
      doHashCycle();
    }
  };

  private final ScheduledCommand defferedExtraNonceDecrementHash = new ScheduledCommand() {
    @Override
    public void execute() {
      decrementExtraNonce();
      doHashCycle();
    }
  };

  private final ScheduledCommand executeHashCommand = new ScheduledCommand() {
    @Override
    public void execute() {
      doFullHashCycle();
    }
  };

  private final RepeatingExecutor executor = new RepeatingExecutor(executeHashCommand);

  private RawBlockContainer rawBlock;

  private RawTransactionContainer coinbase;

  @Inject
  public MineViewImpl() {
    initWidget(UI_BINDER.createAndBindUi(this));
  }

  @Override
  public void setInformation(final Block initialBlock, final RawBlockContainer rawBlock, final RawTransactionContainer coinbase) {
    this.rawBlock = rawBlock;
    this.coinbase = coinbase;

    versionViewer.setValue(initialBlock.getVersion());
    previousBlockHashViewer.setValue(Str.toString(Hex.encode(initialBlock.getPreviousBlockHash())).toUpperCase());
    merkleRootViewer.setValue(Str.toString(Hex.encode(initialBlock.getMerkleRoot())).toUpperCase());
    timestampViewer.setValue(FormatUtil.formatDateTime(initialBlock.getTimestamp()));
    bitsViewer.setValue(initialBlock.getBits());
    nonceViewer.setValue(initialBlock.getNonce());

    final RawBlockContainer viewBlock = rawBlock.copy();

    // Set up viewBlock for display in hex
    blockHexViewer.setContainerMap(viewBlock);
    coinbaseHexViewer.setContainer(coinbase);
    blockHashViewer.setHash(initialBlock.getBlockHash());
  }

  @Override
  public void cancel() {
    executor.cancel();
  }

  @UiHandler("cancelButton")
  public void onCancelClick(final ClickEvent e) {
    cancel();
  }

  @UiHandler("continueButton")
  public void onContinueClick(final ClickEvent e) {
    startHashExecutor();
  }

  @UiHandler("singleCycleButton")
  public void onSingleCycleClick(final ClickEvent e) {
    doSingleCycle();
  }

  @UiHandler("nonceIncrementButton")
  public void onNonceIncrementClick(final ClickEvent e) {
    Scheduler.get().scheduleDeferred(defferedNonceHash);
  }

  @UiHandler("nonceDecrementButton")
  public void onNonceDecrementClick(final ClickEvent e) {
    Scheduler.get().scheduleDeferred(defferedNonceDecrementHash);
  }

  @UiHandler("extraNonceIncrementButton")
  public void onExtraNonceIncrementClick(final ClickEvent e) {
    Scheduler.get().scheduleDeferred(defferedExtraNonceHash);
  }

  @UiHandler("extraNonceDecrementButton")
  public void onExtraNonceDecrementClick(final ClickEvent e) {
    Scheduler.get().scheduleDeferred(defferedExtraNonceDecrementHash);
  }

  @UiHandler("timeSynchronizeButton")
  public void onTimeSyncClick(final ClickEvent e) {
    Scheduler.get().scheduleDeferred(defferedTimeHash);
  }

  private void doSingleCycle() {
    Scheduler.get().scheduleDeferred(executeHashCommand);
  }

  private void startHashExecutor() {
    executor.start(MINING_SIMULATION_DELAY);
  }

  private void incrementNonce() {
    final long nonce = NumberParseUtil.parseUint32(rawBlock.getNonce()) + 1;
    nonceViewer.setValue(nonce);
    rawBlock.setNonce(BlockEncodeUtil.encodeNonce(nonce));
  }

  private void decrementNonce() {
    final long nonce = NumberParseUtil.parseUint32(rawBlock.getNonce()) - 1;
    nonceViewer.setValue(nonce);
    rawBlock.setNonce(BlockEncodeUtil.encodeNonce(nonce));
  }

  private void incrementExtraNonce() {
    final Entry<TransactionPartType, byte[]> find = coinbase.find(TransactionPartType.ARBITRARY_DATA);

    final byte[] value = find.getValue();

    // Let's just take the last 4 bytes
    final byte[] nonceBytes = new byte[4];

    // Get the last four bytes
    System.arraycopy(value, value.length - 4, nonceBytes, 0, 4);

    // Increment the sucker
    final long nonce = NumberParseUtil.parseUint32(nonceBytes) + 1;

    final byte[] encodedNonceBytes = NumberEncodeUtil.encodeUint32(nonce);

    // Stick the encoded result back in
    System.arraycopy(encodedNonceBytes, 0, value, value.length - 4, 4);

    find.setValue(value);

    coinbaseHexViewer.setContainer(coinbase);

    final byte[] computeMerkleRoot = ComputeUtil.computeMerkleRoot(coinbase.getBytes());
    rawBlock.setMerkleRoot(computeMerkleRoot);
    merkleRootViewer.setValue(computeMerkleRoot);
  }

  private void decrementExtraNonce() {
    final Entry<TransactionPartType, byte[]> find = coinbase.find(TransactionPartType.ARBITRARY_DATA);

    final byte[] value = find.getValue();

    // Let's just take the last 4 bytes
    final byte[] nonceBytes = new byte[4];

    // Get the last four bytes
    System.arraycopy(value, value.length - 4, nonceBytes, 0, 4);

    // Increment the sucker
    final long nonce = NumberParseUtil.parseUint32(nonceBytes) - 1;

    final byte[] encodedNonceBytes = NumberEncodeUtil.encodeUint32(nonce);

    // Stick the encoded result back in
    System.arraycopy(encodedNonceBytes, 0, value, value.length - 4, 4);

    find.setValue(value);

    coinbaseHexViewer.setContainer(coinbase);

    final byte[] computeMerkleRoot = ComputeUtil.computeMerkleRoot(coinbase.getBytes());
    rawBlock.setMerkleRoot(computeMerkleRoot);
    merkleRootViewer.setValue(computeMerkleRoot);
  }

  private void synchronizeTime() {
    final Date time = new Date();
    timestampViewer.setValue(FormatUtil.formatDateTime(time));
    rawBlock.setTimestamp(BlockEncodeUtil.encodeTimestamp(time));
  }

  private void doFullHashCycle() {
    incrementNonce();
    synchronizeTime();

    doHashCycle();
  }

  private void doHashCycle() {
    blockHexViewer.setContainerMap(rawBlock);

    final byte[] computeDoubleSHA256 = ComputeUtil.computeDoubleSHA256(rawBlock.values());
    ArrayUtil.reverse(computeDoubleSHA256);

    blockHashViewer.setHash(computeDoubleSHA256);
  }
}
