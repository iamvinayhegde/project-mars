import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.tags.TagManager;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.servlets.annotations.SlingServletPaths;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@SlingServletPaths(value = "/your/servlet/path")
public class PageTagUpdaterServlet extends SlingSafeMethodsServlet {

    private final ResourceResolverFactory resourceResolverFactory;

    public PageTagUpdaterServlet(ResourceResolverFactory resourceResolverFactory) {
        this.resourceResolverFactory = resourceResolverFactory;
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(null)) {
            String jsonPath = "/content/dam/myfolder/myfile.json"; // Update with your JSON file path
            Asset jsonAsset = getJsonAsset(resourceResolver, jsonPath);

            if (jsonAsset == null) {
                sendErrorResponse(response, "JSON file not found in DAM!", SlingHttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String jsonContent = readJsonContent(jsonAsset);

            if (jsonContent == null) {
                sendErrorResponse(response, "Error occurred while reading JSON file!", SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            JsonArray jsonArray = parseJsonContent(jsonContent);

            if (jsonArray == null) {
                sendErrorResponse(response, "Error occurred while parsing JSON content!", SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
            updatePageTags(resourceResolver, jsonArray, tagManager);

            sendSuccessResponse(response, "Tags updated successfully!", SlingHttpServletResponse.SC_OK);
        } catch (Exception e) {
            sendErrorResponse(response, "Error occurred while updating tags: " + e.getMessage(), SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Asset getJsonAsset(ResourceResolver resourceResolver, String jsonPath) {
        AssetManager assetManager = resourceResolver.adaptTo(AssetManager.class);
        return assetManager.getAsset(jsonPath);
    }

    private String readJsonContent(Asset jsonAsset) throws IOException {
        try (InputStream inputStream = jsonAsset.getOriginal().getStream()) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        }
    }

    private JsonArray parseJsonContent(String jsonContent) {
        return JsonParser.parseString(jsonContent).getAsJsonArray();
    }

   private void updatePageTags(ResourceResolver resourceResolver, JsonArray jsonArray, TagManager tagManager) {
    PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
    
    for (JsonElement jsonElement : jsonArray) {
        JsonObject pageJson = jsonElement.getAsJsonObject();
        String pagePath = pageJson.get("path").getAsString();
        JsonArray tagsJson = pageJson.getAsJsonArray("tags");

        Page page = pageManager.getPage(pagePath);
        if (page != null) {
            Resource contentResource = page.getContentResource();
            ModifiableValueMap valueMap = contentResource.adaptTo(ModifiableValueMap.class);

            String[] tags = new Gson().fromJson(tagsJson, String[].class);
            valueMap.put("cq:tags", tags);
        }
    }

    resourceResolver.commit();
}

    private void sendSuccessResponse(SlingHttpServletResponse response, String message, int statusCode) throws IOException {
        response.getWriter().write(message);
        response.setStatus(statusCode);
    }

    private void sendErrorResponse(SlingHttpServletResponse response, String message, int statusCode) throws IOException {
        response.getWriter().write(message);
        response.setStatus(statusCode);
    }
}
