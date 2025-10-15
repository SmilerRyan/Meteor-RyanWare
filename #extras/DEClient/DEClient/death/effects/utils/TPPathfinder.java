package death.effects.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;

@Environment(EnvType.CLIENT)
public class TPPathfinder {
   private static final double DEFAULT_NODE_DISTANCE = 3.0D;
   private static final int MAX_ITERATIONS = 1000;
   private static final double PLAYER_WIDTH = 0.6D;
   private static final double PLAYER_HEIGHT = 1.8D;
   private final class_310 client;
   private final class_1937 world;
   private final double nodeDistance;

   public TPPathfinder(class_310 client) {
      this(client, 3.0D);
   }

   public TPPathfinder(class_310 client, double nodeDistance) {
      this.client = client;
      this.world = client.field_1687;
      this.nodeDistance = nodeDistance;
   }

   public List<class_243> findAndExecutePath(class_243 start, class_243 end, Consumer<class_243> teleportAction) {
      List<class_243> path = this.findPath(start, end);
      if (path.isEmpty()) {
         return Collections.emptyList();
      } else {
         Iterator var5 = path.iterator();

         while(var5.hasNext()) {
            class_243 pos = (class_243)var5.next();
            teleportAction.accept(pos);

            try {
               Thread.sleep(50L);
            } catch (InterruptedException var8) {
               Thread.currentThread().interrupt();
            }
         }

         return path;
      }
   }

   public List<class_243> findPath(class_243 start, class_243 end) {
      if (this.world == null) {
         return Collections.emptyList();
      } else {
         PriorityQueue<TPPathfinder.Node> openSet = new PriorityQueue(Comparator.comparingDouble((n) -> {
            return n.fCost;
         }));
         Set<class_2338> closedSet = new HashSet();
         TPPathfinder.Node startNode = new TPPathfinder.Node(start, (TPPathfinder.Node)null, 0.0D, start.method_1022(end));
         openSet.add(startNode);
         int iterations = 0;

         while(!openSet.isEmpty() && iterations < 1000) {
            ++iterations;
            TPPathfinder.Node current = (TPPathfinder.Node)openSet.poll();
            if (current.position.method_1022(end) < this.nodeDistance) {
               return this.reconstructPath(current);
            }

            class_2338 blockPos = new class_2338((int)current.position.field_1352, (int)current.position.field_1351, (int)current.position.field_1350);
            closedSet.add(blockPos);
            Iterator var9 = this.getDirections().iterator();

            while(var9.hasNext()) {
               class_243 direction = (class_243)var9.next();
               class_243 neighborPos = current.position.method_1019(direction.method_1021(this.nodeDistance));
               class_2338 neighborBlockPos = new class_2338((int)neighborPos.field_1352, (int)neighborPos.field_1351, (int)neighborPos.field_1350);
               if (!closedSet.contains(neighborBlockPos) && this.isPositionValid(neighborPos)) {
                  double gCost = current.gCost + this.nodeDistance;
                  double hCost = neighborPos.method_1022(end);
                  TPPathfinder.Node neighbor = new TPPathfinder.Node(neighborPos, current, gCost, hCost);
                  openSet.add(neighbor);
               }
            }
         }

         return Collections.emptyList();
      }
   }

   private List<class_243> getDirections() {
      List<class_243> directions = new ArrayList();
      directions.add(new class_243(1.0D, 0.0D, 0.0D));
      directions.add(new class_243(-1.0D, 0.0D, 0.0D));
      directions.add(new class_243(0.0D, 0.0D, 1.0D));
      directions.add(new class_243(0.0D, 0.0D, -1.0D));
      directions.add(new class_243(1.0D, 0.0D, 1.0D));
      directions.add(new class_243(1.0D, 0.0D, -1.0D));
      directions.add(new class_243(-1.0D, 0.0D, 1.0D));
      directions.add(new class_243(-1.0D, 0.0D, -1.0D));
      directions.add(new class_243(0.0D, 1.0D, 0.0D));
      directions.add(new class_243(0.0D, -1.0D, 0.0D));
      return directions;
   }

   private boolean isPositionValid(class_243 pos) {
      if (this.world == null) {
         return false;
      } else {
         class_238 box = new class_238(pos.field_1352 - 0.3D, pos.field_1351, pos.field_1350 - 0.3D, pos.field_1352 + 0.3D, pos.field_1351 + 1.8D, pos.field_1350 + 0.3D);

         for(int x = (int)Math.floor(box.field_1323); (double)x < Math.ceil(box.field_1320); ++x) {
            for(int y = (int)Math.floor(box.field_1322); (double)y < Math.ceil(box.field_1325); ++y) {
               for(int z = (int)Math.floor(box.field_1321); (double)z < Math.ceil(box.field_1324); ++z) {
                  class_2338 blockPos = new class_2338(x, y, z);
                  class_2680 blockState = this.world.method_8320(blockPos);
                  if (!blockState.method_26220(this.world, blockPos).method_1110()) {
                     return false;
                  }
               }
            }
         }

         return true;
      }
   }

   private List<class_243> reconstructPath(TPPathfinder.Node endNode) {
      List<class_243> path = new ArrayList();

      for(TPPathfinder.Node current = endNode; current != null; current = current.parent) {
         path.add(current.position);
      }

      Collections.reverse(path);
      return path;
   }

   @Environment(EnvType.CLIENT)
   private static class Node {
      class_243 position;
      TPPathfinder.Node parent;
      double gCost;
      double hCost;
      double fCost;

      Node(class_243 position, TPPathfinder.Node parent, double gCost, double hCost) {
         this.position = position;
         this.parent = parent;
         this.gCost = gCost;
         this.hCost = hCost;
         this.fCost = gCost + hCost;
      }
   }
}
