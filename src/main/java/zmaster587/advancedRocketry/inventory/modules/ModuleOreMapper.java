package zmaster587.advancedRocketry.inventory.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;

import zmaster587.advancedRocketry.client.render.ClientDynamicTexture;
import zmaster587.advancedRocketry.satellite.SatelliteOreMapping;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.render.RenderHelper;

import java.nio.IntBuffer;

@OnlyIn(value=Dist.CLIENT)
public class ModuleOreMapper extends ModuleBase {

	ClientDynamicTexture texture;
	Thread currentMapping;
	TileEntity masterConsole;
	boolean merged = false;
	private static final int SCREEN_SIZE = 146;
	private static final int MAXZOOM = 128;
	private static final int MAXRADIUS = 16;
	private static final int FANCYSCANMAXSIZE = 57;
	private int fancyScanOffset;
	private long prevWorldTickTime;
	private int prevSlot;
	private int mouseValue;
	private int scanSize = 32;
	private int radius = 1;
	private int zoomScale = 1;
	private int maxZoom = 1;
	private int xSelected, zSelected, xCenter, zCenter;
	private static final ResourceLocation backdrop = new ResourceLocation("advancedrocketry", "textures/gui/VideoSatallite.png");
	int[][] oreMap;
	World world;
	SatelliteOreMapping tile;
	ItemStack selectedStack;
	SatelliteOreMapping satellite;
	
	public ModuleOreMapper(int offsetX, int offsetY, SatelliteOreMapping satellite) {
		super(offsetX, offsetY);
		world = Minecraft.getInstance().world;

		prevSlot = -1;
		this.tile = tile;
		//masterConsole = tile;
		//xCenter = tile.getBlockCenterX();
		//zCenter = tile.getBlockCenterZ();
		
		//Max zoom is 128
		if(satellite != null) {
			maxZoom = (int) Math.pow(2, satellite.getZoomRadius());
			zoomScale = satellite.getZoomRadius();
		}

		if(maxZoom == 1)
			this.satellite = null;
		scanSize = maxZoom;
		
		prevWorldTickTime = world.getGameTime();
		
		fancyScanOffset = 0;
	}

	//Create separate thread to do this because it takes a while!
	Runnable mapper = new Runnable() {
		@Override
		public void run() {
			oreMap = satellite.scanChunk(world, xCenter, zCenter, scanSize/2, radius, zoomScale);
			merged = oreMap != null && !Thread.interrupted();
		}
	};

	//Create separate thread to do this because it takes a while!
	class ItemMapper implements Runnable {
		private ItemStack myBlock;

		ItemMapper(ItemStack block) {
			//Copy so we dont have any possible CME or oddness due to that
			myBlock = block.copy();
		}

		@Override
		public void run() {
			oreMap = satellite.scanChunk(world, xCenter, zCenter, scanSize/2, radius, myBlock, zoomScale);
			merged = oreMap != null;
		}
	}

	private void runMapperWithSelection() {
		currentMapping.interrupt();
		resetTexture();
		if(prevSlot == -1) {
			currentMapping = new Thread(mapper);
		}
		else {
			//currentMapping = new Thread(new ItemMapper(inventorySlots.getSlot(prevSlot).getStack()));//TODO
		}
		currentMapping.setName("Ore Scan");
		currentMapping.start();
	}
	
	//Reset the texture and prevent memory leaks
	private void resetTexture() {
		GL11.glDeleteTextures(texture.getTextureId());
		texture = new ClientDynamicTexture(Math.max(scanSize/radius,1),Math.max(scanSize/radius,1));
	}
	
	@Override
	public void renderForeground(MatrixStack matrix, int guiOffsetX, int guiOffsetY, int mouseX, int mouseY,
			float zLevel, ContainerScreen<? extends Container> gui, FontRenderer font)  {
		super.renderForeground(matrix, guiOffsetX, guiOffsetY, mouseX, mouseY, zLevel, gui, font);
		
		BufferBuilder buffer = Tessellator.getInstance().getBuffer();
		
		//Draw fancy things
		GlStateManager.disableTexture();
		buffer.color(0f, 0.8f, 0f, 1f);
		buffer.begin(GL11.GL_QUADS, buffer.getVertexFormat());
		buffer.pos(-21, 82 + fancyScanOffset, zLevel).endVertex();
		buffer.pos(0, 84 + fancyScanOffset, zLevel).endVertex();
		buffer.pos(0, 81 + fancyScanOffset, zLevel).endVertex();
		buffer.pos(-21, 81 + fancyScanOffset, zLevel).endVertex();
		buffer.finishDrawing();
		
		buffer.begin(GL11.GL_QUADS, buffer.getVertexFormat());
		buffer.pos(-21, 82 - fancyScanOffset + FANCYSCANMAXSIZE, zLevel).endVertex();
		buffer.pos(0, 84 - fancyScanOffset + FANCYSCANMAXSIZE, zLevel).endVertex();
		buffer.pos(0, 81 - fancyScanOffset + FANCYSCANMAXSIZE, zLevel).endVertex();
		buffer.pos(-21, 81 - fancyScanOffset + FANCYSCANMAXSIZE, zLevel).endVertex();
		buffer.finishDrawing();
		
		
		GlStateManager.enableBlend();
		
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_DST_ALPHA);
		buffer.color(0.5f, 0.5f, 0.0f,0.3f + ((float)Math.sin(Math.PI*(fancyScanOffset/(float)FANCYSCANMAXSIZE))/3f));
		buffer.begin(GL11.GL_QUADS, buffer.getVertexFormat());
		RenderHelper.renderNorthFace(matrix, buffer, zLevel, 173, 82, 194, 141,1,1,1,1);
		buffer.finishDrawing();
		
		GlStateManager.enableTexture();
		GlStateManager.disableBlend();
		
		
		if(world.getGameTime() - prevWorldTickTime >= 1 ) {
			prevWorldTickTime = world.getGameTime();
			if(fancyScanOffset >= FANCYSCANMAXSIZE)
				fancyScanOffset = 0;
			else
				fancyScanOffset++;
		}
		
		
		//If a slot is selected draw an indicator
		int slot;
		if((slot = tile.getSelectedSlot()) != -1) {

			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glColor3f(0f, 0.8f, 0f);
			
			buffer.begin(GL11.GL_QUADS, buffer.getVertexFormat());
			RenderHelper.renderNorthFaceWithUV(matrix, buffer, zLevel, 13 + (18*slot), 155, 13 + 16 + (18*slot), 155 + 16, 0, 1, 0, 1);
			buffer.finishDrawing();
			GL11.glEnable(GL11.GL_TEXTURE_2D);
		}
	}
	
	@Override
	public void renderBackground(ContainerScreen<? extends Container> gui, MatrixStack matrix, int x, int y, int mouseX,
			int mouseY, FontRenderer font) {
		super.renderBackground(gui, matrix, x, y, mouseX, mouseY, font);
		
		//int x = (width - 240) / 2, y = (height - 192) / 2;

		//If the scan is done then 
		if(merged) {
			IntBuffer buffer = texture.getByteBuffer();
			int scanWidth = Math.max(scanSize/radius,1);

			for(int yt = 0; yt < (texture.getHeight() * texture.getWidth()); yt++) {
				buffer.put(yt, oreMap[yt % scanWidth][yt / scanWidth] | 0xFF000000);
			}
			buffer.flip();
			texture.setByteBuffer(buffer);
			merged = false;
		}


		//Render the background then render
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getInstance().getTextureManager().bindTexture(backdrop);
		gui.blit(matrix, x, y, 0, 0, 240, 192);


		//NOTE: if the controls are rendered first the display never shows up
		//Draw the actual display
		int zLevel = 100;
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
		BufferBuilder buffer = Tessellator.getInstance().getBuffer();
		buffer.begin(GL11.GL_QUADS, buffer.getVertexFormat());
		RenderHelper.renderNorthFaceWithUV(matrix, buffer, zLevel, 47 + x,  20 + y, 47 + x + SCREEN_SIZE,  20 + y + SCREEN_SIZE, 0, 1, 0, 1);
		buffer.finishDrawing();


		//Render sliders and controls
		Minecraft.getInstance().getTextureManager().bindTexture(backdrop);

		gui.blit(matrix, 197 + x, 31 + y, 0, 192, 32, 14);
		
		//TODO replace with thing
		//gui.drawVerticalLine((int)(32*VulpineMath.log2(scanSize-1)/8F) + 199 + x, 34 + y, 45 + y, 0xFFC00F0F);

		font.drawTextWithShadow(matrix, new StringTextComponent("Zoom"), 198 + x, 22 + y, 0xF0F0F0);

		font.drawTextWithShadow(matrix, new StringTextComponent("X: " + xSelected), 6 + x, 33 + y, 0xF0F0F0);
		font.drawTextWithShadow(matrix, new StringTextComponent("Z: " + zSelected), 6 + x, 49 + y, 0xF0F0F0);
		font.drawTextWithShadow(matrix, new StringTextComponent("Value: "), 6 + x, 65 + y, 0xF0F0F0);
		font.drawTextWithShadow(matrix, new StringTextComponent(String.valueOf(mouseValue)), 6 + x, 79 + y, 0xF0F0F0);
	}
	
}
