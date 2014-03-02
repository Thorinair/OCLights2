package ds.mods.CCLights2.gpu;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Stack;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.gpu.imageLoader.ImageLoader;
import ds.mods.CCLights2.network.PacketSenders;

public class GPU {
	public Texture[] textures;
	public int maxmem;
	public Deque<DrawCMD> drawlist;
	public int drawlisthash;
	public Texture bindedTexture;
	public int bindedSlot;
	public ArrayList<Monitor> monitors = new ArrayList<Monitor>();
	public Monitor currentMonitor;
	public TileEntityGPU tile;
	public UUID uuid;
	public Color color = Color.white;
	public Stack<AffineTransform> transformStack = new Stack<AffineTransform>();
	public AffineTransform transform = new AffineTransform();

	public GPU(int gfxmem)
	{
		textures = new Texture[8192];
		drawlist = new ArrayDeque<DrawCMD>();
		maxmem = gfxmem;
	}
	
	public Monitor getMonitor() {
		return currentMonitor;
	}
	
	public void addMonitor(Monitor mon)
	{
		monitors.add(mon);
		currentMonitor = mon;
		CCLights2.debug("Added monitor "+mon.getWidth()+"x"+mon.getHeight()+" "+mon);
	}
	
	public void removeMonitor(Monitor mon)
	{
		monitors.remove(mon);
		CCLights2.debug("Rem monitor "+mon.getWidth()+"x"+mon.getHeight()+" "+mon);
		if (currentMonitor == mon)
		{
			textures[0] = null;
			bindedTexture = null;
			currentMonitor = null;
			for (Monitor m : monitors)
			{
				currentMonitor = m; break;
			}
		}
		mon.tex.fill(Color.black);
	}

	public void setMonitor(Monitor mon) {
		if (!monitors.contains(mon))
		{
			addMonitor(mon);
		}
		this.currentMonitor = mon;
		CCLights2.debug("Monitor set!");
		bindedTexture = mon.getTex();
		textures[0] = bindedTexture;
		bindedSlot = 0;
	}
	
	public int getUsedMemory()
	{
		int used = 0;
		for (int i=1; i<textures.length; i++)
		{
			if (textures[i]!=null)
			{
				used+=textures[i].getMemoryUse();
			}
		}
		return used;
	}
	
	public int getFreeMemory()
	{
		return maxmem-getUsedMemory();
	}
	
	public void bindTexture(int texid) throws Exception
	{
		if (textures[texid] == null)
			throw new Exception("Texture doesn't exist!");
		bindedTexture = textures[texid];
		bindedSlot = texid;
	}
	
	public int newTexture(int w, int h)
	{
		if (getFreeMemory()<0)
		{
			return -1;
		}
		else
		{
			for (int i=1; i<textures.length; i++)
			{
				if (textures[i]==null)
				{
					textures[i] = new Texture(w, h);
					return i;
				}
			}
			return -2;
		}
	}
	
	public void push()
	{
		transformStack.push(transform);
		transform = (AffineTransform) transform.clone();
	}
	
	public void pop()
	{
		transform = transformStack.pop();
	}
	
	public void translate(double x, double y)
	{
		transform.translate(x, y);
	}
	
	public void rotate(double r)
	{
		transform.rotate(r);
	}
	
	public void rotate(double r, double x, double y)
	{
		transform.rotate(r,x,y);
	}
	
	public void scale(double s)
	{
		transform.scale(s, s);
	}
	
	public void scale(double sx, double sy)
	{
		transform.scale(sx, sy);
	}
	
	public Object[] processCommand(DrawCMD cmd) throws Exception
	{
		if (cmd == null)
			return null;
		if (bindedTexture == null)
		{
			return null;
		}
			bindedTexture.transform = transform;
			switch(cmd.cmd)
			{
				case -1:
				{
					color = cmd.args.length == 3 ? new Color((Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2]) : new Color((Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case 0:
				{
					//Clear//
					bindedTexture.fill(color);
					break;
				}
				case 1:
				{
					//Plot//
					bindedTexture.plot(color,(Integer) cmd.args[0],(Integer) cmd.args[1]);
					break;
				}
				case 2:
				{
					//drawTexture//
					if ((Integer)cmd.args[0] == 0)
					{
						//Small version//
						bindedTexture.drawTexture(textures[(Integer) cmd.args[1]], (Integer) cmd.args[2],(Integer) cmd.args[3], color);
					}
					else
					{
						bindedTexture.drawTexture(textures[(Integer) cmd.args[1]], (Integer) cmd.args[2],(Integer) cmd.args[3], (Integer) cmd.args[4],(Integer) cmd.args[5], (Integer) cmd.args[6],(Integer) cmd.args[7],color);
					}
					break;
				}
				case 3:
				{
					//line//
					bindedTexture.line(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case 6:
				{
					//New Texture//
					return new Object[]{newTexture((Integer) cmd.args[0],(Integer) cmd.args[1])};
				}
				case 7:
				{
					//Bind Texture//
					bindedTexture = textures[(Integer) cmd.args[0]];
					bindedSlot = (Integer) cmd.args[0];
					break;
				}
				case 8:
				{
					//Delete Texture//
					if (bindedTexture == textures[(Integer) cmd.args[0]])
					{
						bindedTexture = textures[0];
						bindedSlot = 0;
					}
					textures[(Integer) cmd.args[0]] = null;
					break;
				}
				case 9:
				{
					bindedTexture.rect(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case 10:
				{
					bindedTexture.filledRect(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case 12:
				{
					int i = 5;
					int type = (Integer) cmd.args[0];
					if (type == 0)
					{
						for (int x = 0; x<(Integer)cmd.args[1]; x++)
						{
							for (int y = 0; y<(Integer)cmd.args[2]; y++)
							{
								bindedTexture.plot(new Color((Integer) cmd.args[i++], (Integer) cmd.args[i++], (Integer) cmd.args[i++]), x+(Integer)cmd.args[3], y+(Integer)cmd.args[4]);
							}
						}
					}
					else
					{
						for (int y = 0; y<(Integer)cmd.args[2]; y++)
						{
							for (int x = 0; x<(Integer)cmd.args[1]; x++)
							{
								bindedTexture.plot(new Color((Integer) cmd.args[i++], (Integer) cmd.args[i++], (Integer) cmd.args[i++]), x+(Integer)cmd.args[3], y+(Integer)cmd.args[4]);
							}
						}
					}
					break;
				}
				case 13:
				{
					textures[(Integer) cmd.args[0]].flipV();
					break;
				}
				case 14:
				{
					if (cmd.args[0] instanceof Object[])
					{
						Object[] old = (Object[]) cmd.args[0];
						cmd.args[0] = new Byte[old.length];
						Byte[] n = (Byte[]) cmd.args[0];
						for (int i=0; i<old.length; i++)
						{
							n[i] = (Byte) old[i];
						}
					}
					BufferedImage img = ImageLoader.load(ArrayUtils.toPrimitive((Byte[])cmd.args[0]), (String)cmd.args[1]);
					int id = newTexture(img.getWidth(),img.getHeight());
					if (id == -1) {
						throw new Exception("Not enough memory for texture");
					} else if (id == -2) {
						throw new Exception("Not enough texture slots");
					}
					Texture tex = textures[id];
					tex.graphics.drawImage(img, 0, 0, null);
					return new Object[]{id};
				}
				case 15:
				{
					String str = "";
					for (int i = 0; i<cmd.args.length-2; i++)
					{
						str = str+String.valueOf((Character)cmd.args[2+i]);
					}
					bindedTexture.drawText(str, (Integer) cmd.args[0], (Integer) cmd.args[1], color);
					break;
				}
				case 16:
				{
					translate((Double)cmd.args[0],(Double)cmd.args[1]);
					break;
				}
				case 17:
				{
					rotate((Double)cmd.args[0]);
					break;
				}
				case 18:
				{
					rotate((Double)cmd.args[0],(Double)cmd.args[1],(Double)cmd.args[2]);
					break;
				}
				case 19:
				{
					scale((Double)cmd.args[0],(Double)cmd.args[1]);
					break;
				}
				case 20:
				{
					push();
					break;
				}
				case 21:
				{
					pop();
					break;
				}
				case 22:
				{
					textures[(Integer) cmd.args[0]].blur();
					break;
				}
				case 23:
				{
					bindedTexture.clearRect(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
			}
		return null;
	}
	
	public void processSendList()
	{
		if (!drawlist.isEmpty())
		{
			if (!tile.worldObj.isRemote)
			{
		    	PacketSenders.sendPacketsNow(drawlist,tile);
			}
			drawlist.clear();
		}
	}
}
